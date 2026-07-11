package com.lzzh.monitor.collector.alert;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.collector.connection.TargetDataSourceFactory;
import com.lzzh.monitor.collector.spi.TargetConnectionCache;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.service.alert.SqlSafetyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 SQL 规则评估（single/multi 维度）。
 * 从 AlertEvaluateJobHandler 拆出；目标库连接复用采集侧 {@link TargetConnectionCache}。
 * <p><b>并发约定</b>：JDBC {@link Connection} 非线程安全，评估期间必须持有
 * {@link TargetConnectionCache#instanceLock}，与采集 Job、连接健康检查互斥；
 * 均为秒级只读短查询，锁等待时间有界。
 */
@Service
public class AlertCustomSqlEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlertCustomSqlEvaluator.class);

    private static final int CUSTOM_SQL_QUERY_TIMEOUT_SECONDS = 10;
    private static final int CUSTOM_SQL_MAX_ROWS = 1000;

    private final TargetConnectionCache connectionCache;
    private final AlertEventLifecycleService lifecycleService;

    public AlertCustomSqlEvaluator(TargetConnectionCache connectionCache,
                                   AlertEventLifecycleService lifecycleService) {
        this.connectionCache = connectionCache;
        this.lifecycleService = lifecycleService;
    }

    /** 规则条件是否为自定义 SQL 模式。 */
    public static boolean hasCustomSql(AlertRule rule) {
        if (rule.getConditionConfig() == null) {
            return false;
        }
        Object customSql = rule.getConditionConfig().get("customSql");
        return customSql instanceof String s && StringUtils.hasText(s);
    }

    /**
     * 执行自定义 SQL 规则评估（single/multi），并处理维度级触发/恢复。
     */
    public AlertEvalCounter evaluate(AlertEvalContext ctx, AlertRule rule, Long instanceId,
                                     String instanceName, CollectTargetVo target) {
        Map<String, Object> cond = rule.getConditionConfig() == null ? Collections.emptyMap() : rule.getConditionConfig();
        Object customSqlObj = cond.get("customSql");
        String customSql = customSqlObj instanceof String s ? s.trim() : "";
        if (customSql.isBlank()) {
            return AlertEvalCounter.EMPTY;
        }
        try {
            SqlSafetyValidator.validateQueryOnly(customSql);
        } catch (IllegalArgumentException e) {
            log.warn("自定义规则 [{}] SQL 安全校验失败: {}", rule.getRuleCode(), e.getMessage());
            // 与建连失败同口径标记 sql_error：配置错误应在界面可见，而非让活跃事件静默挂起到自愈
            lifecycleService.markSqlError(ctx, rule, instanceId, "SQL 安全校验失败：" + e.getMessage());
            return AlertEvalCounter.EMPTY;
        }
        Object modeObj = cond.get("resultMode");
        String resultMode = modeObj instanceof String s ? s.trim().toLowerCase() : "single";
        Object singleFieldObj = cond.get("sqlReturnField");
        String sqlReturnField = singleFieldObj instanceof String s ? s.trim() : "";
        Object entityObj = cond.get("entityColumn");
        String entityColumn = entityObj instanceof String s ? s.trim() : "";
        Object valueObj = cond.get("valueColumn");
        String valueColumn = valueObj instanceof String s ? s.trim() : "";
        if (target == null) {
            log.warn("自定义规则 [{}] 未找到实例连接信息，instanceId={}", rule.getRuleCode(), instanceId);
            // 与建连失败同口径标记 sql_error：否则已有活跃事件会静默挂起到自愈兜底，界面无失败原因
            lifecycleService.markSqlError(ctx, rule, instanceId, "未找到实例连接信息");
            return AlertEvalCounter.EMPTY;
        }

        // 持实例锁读取结果集（与采集 Job、健康检查互斥）；触发/恢复处理在锁外进行，
        // 避免持锁执行事件落库与通知等慢操作，缩短采集线程的锁等待。
        List<ResultRow> rows = new ArrayList<>();
        String sqlError = null;
        var lock = connectionCache.instanceLock(instanceId);
        lock.lock();
        try {
            Connection conn = null;
            try {
                conn = connectionCache.borrow(instanceId, TargetDataSourceFactory.from(target));
            } catch (Exception e) {
                log.warn("自定义规则 [{}] 建连目标库失败 instanceId={} err={}",
                        rule.getRuleCode(), instanceId, e.getMessage());
                sqlError = "建连目标库失败：" + e.getMessage();
            }
            if (conn != null) {
                // 缓存连接由 TargetConnectionCache 统一管理，此处不 close；仅 Statement/ResultSet 释放
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(CUSTOM_SQL_QUERY_TIMEOUT_SECONDS);
                    stmt.setMaxRows(CUSTOM_SQL_MAX_ROWS);
                    try (ResultSet rs = stmt.executeQuery(customSql)) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        while (rs.next()) {
                            String dimensionKey = null;
                            Double value;
                            if ("multi".equals(resultMode)) {
                                String dimCol = entityColumn.isBlank() ? (colCount >= 1 ? meta.getColumnLabel(1) : "") : entityColumn;
                                String valCol = valueColumn.isBlank() ? (colCount >= 2 ? meta.getColumnLabel(2) : "") : valueColumn;
                                Object dimObj = dimCol.isBlank() ? null : rs.getObject(dimCol);
                                dimensionKey = dimObj == null ? null : String.valueOf(dimObj);
                                value = readNumeric(rs, valCol, colCount >= 2 ? 2 : 1);
                            } else {
                                value = sqlReturnField.isBlank() ? readNumeric(rs, "", 1) : readNumeric(rs, sqlReturnField, 1);
                            }
                            if (value == null) {
                                continue;
                            }
                            String finalDimensionKey = StringUtils.hasText(dimensionKey) ? dimensionKey.trim() : null;
                            rows.add(new ResultRow(finalDimensionKey, value));
                        }
                    }
                } catch (Exception e) {
                    // 连接可能已损坏，驱逐以便下次重建
                    connectionCache.evict(instanceId);
                    log.warn("自定义规则 [{}] SQL 执行失败 instanceId={} err={}",
                            rule.getRuleCode(), instanceId, e.getMessage());
                    sqlError = "SQL 执行失败：" + e.getMessage();
                }
            }
        } finally {
            lock.unlock();
        }
        if (sqlError != null) {
            lifecycleService.markSqlError(ctx, rule, instanceId, sqlError);
            return AlertEvalCounter.EMPTY;
        }

        AlertEvalCounter counter = AlertEvalCounter.EMPTY;
        Map<String, Double> latestByDimension = new HashMap<>();
        for (ResultRow row : rows) {
            String finalDimensionKey = row.dimensionKey();
            double value = row.value();
            latestByDimension.put(finalDimensionKey == null ? "" : finalDimensionKey, value);
            boolean triggered = AlertConditionEvaluator.checkCondition(rule.getConditionConfig(), value);
            if (triggered) {
                AlertUpsertResult upsert = lifecycleService.handleTriggerWithWindow(
                        ctx, rule, instanceId, instanceName, finalDimensionKey, value);
                if (upsert.windowPending() || upsert.disposedSkipped()) {
                    continue;
                }
                if (upsert.silenceSuppressed()) {
                    counter = counter.addSilenced();
                } else {
                    counter = counter.addTriggered(upsert.notified());
                }
            } else {
                // 对齐指标规则的恢复语义：不能简单地"未触发即视为恢复"，
                // 必须同样经过 checkRecovery（recoveryConfig 留空则以条件反义恢复），
                // 否则自定义 SQL 规则会绕过恢复阈值/滞回区间，造成刚脱离触发阈值就立即恢复。
                boolean shouldRecover = AlertConditionEvaluator.checkRecovery(
                        rule.getConditionConfig(), rule.getRecoveryConfig(), value);
                if (shouldRecover) {
                    if (lifecycleService.handleRecoverWithWindow(ctx, rule, instanceId, instanceName, finalDimensionKey, value)) {
                        counter = counter.addRecovered();
                    }
                } else {
                    // 滞回区间（既不触发也不满足恢复）：与指标规则一致，重置持续窗口并清理评估异常态
                    lifecycleService.clearTriggerPending(ctx, rule, instanceId, finalDimensionKey);
                }
            }
        }

        // 结果集中已消失的维度按恢复处理（single 模式同样适用：查询成功但不再返回数值行，
        // 说明触发对象已不存在，如 "仅在异常时返回行" 的 SQL；否则该活跃事件将永久挂起）。
        // 恢复持续窗口（recoveryDuration）仍然生效，瞬时空结果不会立即误恢复。
        List<AlertEvent> activeEvents = lifecycleService.listActiveEvents(ctx, rule, instanceId);
        for (AlertEvent active : activeEvents) {
            String dimensionKey = StringUtils.hasText(active.getDimensionKey()) ? active.getDimensionKey().trim() : "";
            if (!latestByDimension.containsKey(dimensionKey)) {
                if (lifecycleService.handleRecoverWithWindow(ctx, rule, instanceId, instanceName, active.getDimensionKey(), 0D)) {
                    counter = counter.addRecovered();
                }
            }
        }
        return counter;
    }

    /** 持锁阶段读出的一行结果，锁外再做触发/恢复处理。 */
    private record ResultRow(String dimensionKey, double value) {}

    private Double readNumeric(ResultSet rs, String columnLabel, int fallbackIndex) {
        try {
            Object raw = StringUtils.hasText(columnLabel) ? rs.getObject(columnLabel) : rs.getObject(fallbackIndex);
            if (raw == null) {
                return null;
            }
            if (raw instanceof Number n) {
                return n.doubleValue();
            }
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
