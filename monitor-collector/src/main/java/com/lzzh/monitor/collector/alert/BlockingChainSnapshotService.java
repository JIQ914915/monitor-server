package com.lzzh.monitor.collector.alert;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.service.instance.InstanceService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 事件驱动阻塞链现场快照（差距分析 模块4）：
 * 锁相关告警/场景事件建单成功后，异步即席连接目标实例抓取当前阻塞链
 * （谁阻塞谁、等待时长、被锁对象、双方 SQL），JSONB 落到 alert_event.blocking_chain_snapshot，
 * 供下钻页渲染"阻塞链现场"卡片。
 *
 * <p>版本适配（§5.8）：
 * <ul>
 *   <li>5.6：information_schema.innodb_lock_waits JOIN innodb_trx / innodb_locks；</li>
 *   <li>5.7：sys.innodb_lock_waits（locked_table 单列）；</li>
 *   <li>8.0：sys.innodb_lock_waits（locked_table_schema/name 拆列，拼接输出）。</li>
 * </ul>
 *
 * <p>抓取失败（连接失败/权限不足/超时）只在快照中记录 error，不影响告警事件主流程。
 */
@Service
public class BlockingChainSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(BlockingChainSnapshotService.class);

    /** 锁相关指标编码：命中任一即触发抓取。 */
    private static final Set<String> LOCK_METRICS = Set.of(
            "mysql.innodb.lock_waits",
            "mysql.innodb.blocked_sessions",
            "mysql.innodb.trx_max_seconds",
            "mysql.innodb.lock_timeout_count",
            "mysql.innodb.deadlock_count");

    /** 锁相关场景编码。 */
    private static final String LOCK_SCENARIO = "scenario.lock_contention";

    private static final int QUERY_TIMEOUT_SECONDS = 10;
    private static final int MAX_ROWS = 50;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AlertEventMapper alertEventMapper;
    @Resource
    private InstanceService instanceService;

    /**
     * 单线程 + 有界队列：快照抓取是低频动作（仅锁相关事件建单时触发），
     * 串行即可，队列满时丢弃并记日志，绝不阻塞告警评估主流程。
     */
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "blocking-chain-snapshot");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** 判断事件是否锁相关（规则指标命中或场景编码命中），是则值得抓取阻塞链现场。 */
    public static boolean isLockRelated(String metricName, String scenarioCode) {
        if (StringUtils.hasText(metricName) && LOCK_METRICS.contains(metricName)) {
            return true;
        }
        return LOCK_SCENARIO.equals(scenarioCode);
    }

    /** 异步抓取阻塞链现场并回写事件行（建单成功后调用，失败不影响主流程）。 */
    public void captureAsync(Long eventId, Long instanceId) {
        try {
            executor.execute(() -> capture(eventId, instanceId));
        } catch (Exception e) {
            log.warn("阻塞链快照任务提交失败 eventId={} instanceId={}: {}", eventId, instanceId, e.getMessage());
        }
    }

    void capture(Long eventId, Long instanceId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("capturedAt", OffsetDateTime.now().format(TS_FMT));
        try {
            CollectTargetVo target = instanceService.getCollectTarget(instanceId);
            if (target == null) {
                snapshot.put("error", "实例不存在或已删除");
            } else {
                snapshot.put("dbVersion", target.getDbVersion());
                List<Map<String, Object>> rows = queryBlockingChain(target);
                snapshot.put("total", rows.size());
                snapshot.put("rows", rows);
            }
        } catch (Exception e) {
            // 面向用户的错误文案：区分权限不足与一般失败（小白用户友好原则）
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (msg.contains("denied") || msg.contains("Access")) {
                snapshot.put("error", "抓取失败：采集账号缺少查询锁等待视图的权限（需 PROCESS 权限及 sys/performance_schema 访问权）");
            } else {
                snapshot.put("error", "抓取失败：" + truncate(msg, 300));
            }
            log.warn("阻塞链现场抓取失败 eventId={} instanceId={}: {}", eventId, instanceId, msg);
        }
        writeSnapshot(eventId, snapshot);
    }

    private List<Map<String, Object>> queryBlockingChain(CollectTargetVo target) throws Exception {
        String url = buildJdbcUrl(target);
        String sql = blockingChainSql(target.getDbVersion());
        List<Map<String, Object>> rows = new ArrayList<>();
        DriverManager.setLoginTimeout(5);
        try (Connection conn = DriverManager.getConnection(url, target.getConnUser(), target.getConnPassword());
             Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            st.setMaxRows(MAX_ROWS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next() && rows.size() < MAX_ROWS) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("waitAgeSecs", rs.getObject("wait_age_secs"));
                    row.put("lockedTable", rs.getString("locked_table"));
                    row.put("lockedType", rs.getString("locked_type"));
                    row.put("waitingPid", rs.getObject("waiting_pid"));
                    row.put("waitingQuery", truncate(rs.getString("waiting_query"), 500));
                    row.put("blockingPid", rs.getObject("blocking_pid"));
                    row.put("blockingQuery", truncate(rs.getString("blocking_query"), 500));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * 版本差异 SQL：统一输出列
     * wait_age_secs / locked_table / locked_type / waiting_pid / waiting_query / blocking_pid / blocking_query。
     */
    static String blockingChainSql(String dbVersion) {
        String v = dbVersion == null ? "" : dbVersion.trim();
        if (v.startsWith("5.6")) {
            // 5.6 无 sys schema：information_schema 三表 JOIN 还原阻塞链
            return """
                    SELECT TIMESTAMPDIFF(SECOND, rt.trx_wait_started, NOW()) AS wait_age_secs,
                           l.lock_table  AS locked_table,
                           l.lock_mode   AS locked_type,
                           rt.trx_mysql_thread_id AS waiting_pid,
                           rt.trx_query  AS waiting_query,
                           bt.trx_mysql_thread_id AS blocking_pid,
                           bt.trx_query  AS blocking_query
                    FROM information_schema.innodb_lock_waits w
                    JOIN information_schema.innodb_trx rt ON rt.trx_id = w.requesting_trx_id
                    JOIN information_schema.innodb_trx bt ON bt.trx_id = w.blocking_trx_id
                    LEFT JOIN information_schema.innodb_locks l ON l.lock_id = w.requested_lock_id
                    ORDER BY wait_age_secs DESC""";
        }
        if (v.startsWith("5.7")) {
            return """
                    SELECT wait_age_secs, locked_table, locked_type,
                           waiting_pid, waiting_query, blocking_pid, blocking_query
                    FROM sys.innodb_lock_waits
                    ORDER BY wait_age_secs DESC""";
        }
        // 8.0+：locked_table 拆为 schema/name 两列
        return """
                SELECT wait_age_secs,
                       CONCAT(locked_table_schema, '.', locked_table_name) AS locked_table,
                       locked_type,
                       waiting_pid, waiting_query, blocking_pid, blocking_query
                FROM sys.innodb_lock_waits
                ORDER BY wait_age_secs DESC""";
    }

    private void writeSnapshot(Long eventId, Map<String, Object> snapshot) {
        try {
            String json = cn.hutool.json.JSONUtil.toJsonStr(snapshot);
            alertEventMapper.update(null, new LambdaUpdateWrapper<AlertEvent>()
                    .eq(AlertEvent::getId, eventId)
                    .set(AlertEvent::getBlockingChainSnapshot, json));
        } catch (Exception e) {
            log.warn("阻塞链快照落库失败 eventId={}: {}", eventId, e.getMessage());
        }
    }

    private static String buildJdbcUrl(CollectTargetVo target) {
        String template = target.getUrlTemplate();
        if (!StringUtils.hasText(template)) {
            throw new IllegalStateException("实例 " + target.getId() + " 对应数据库类型缺少 urlTemplate");
        }
        return template
                .replace("{host}", target.getHost() == null ? "" : target.getHost())
                .replace("{port}", target.getPort() == null ? "" : String.valueOf(target.getPort()))
                .replace("{database}", target.getDatabaseName() == null ? "" : target.getDatabaseName());
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
