package com.lzzh.monitor.collector.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzzh.monitor.collector.alert.AlertConditionEvaluator;
import com.lzzh.monitor.collector.alert.AlertCustomSqlEvaluator;
import com.lzzh.monitor.collector.alert.AlertEvalContext;
import com.lzzh.monitor.collector.alert.AlertEvalCounter;
import com.lzzh.monitor.collector.alert.AlertEventLifecycleService;
import com.lzzh.monitor.collector.alert.AlertUpsertResult;
import com.lzzh.monitor.dao.entity.AlertEvaluateWindow;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.common.enums.InstanceStatus;
import com.lzzh.monitor.dao.entity.AlertEventOperateLog;
import com.lzzh.monitor.dao.entity.AlertRuleInstanceConfig;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.dao.entity.MonitorScenario;
import com.lzzh.monitor.dao.entity.ScenarioInstanceConfig;
import com.lzzh.monitor.dao.mapper.AlertEvaluateWindowMapper;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertEventOperateLogMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleInstanceConfigMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleMapper;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.mapper.MetricDefinitionMapper;
import com.lzzh.monitor.dao.mapper.MonitorScenarioMapper;
import com.lzzh.monitor.dao.mapper.ScenarioInstanceConfigMapper;
import com.lzzh.monitor.dao.ts.MetricValueQueryDao;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.service.instance.InstanceService;
import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator;
import com.lzzh.monitor.service.scenario.ScenarioEvaluationService;
import jakarta.annotation.Resource;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 告警规则评估任务（评估主流程）。
 * <p>每分钟运行一次（xxl-job 分片广播），对所有启用的告警规则：
 * <ol>
 *   <li>解析作用范围 → 确定待评估实例列表；</li>
 *   <li>从 metric_data_1m/1h/1d 取最新值；</li>
 *   <li>按 conditionConfig 做阈值判断：触发则 upsert alert_event（dedup_key 去重）；</li>
 *   <li>若有活跃事件且已满足 recoveryConfig 则标记恢复。</li>
 * </ol>
 *
 * <p>conditionConfig 结构示例：{@code {"operator":">","threshold":90.0}}
 * <p>recoveryConfig 结构示例：{@code {"operator":"<=","threshold":85.0}}；留空则以条件反义作恢复。
 * <p>当前模型：内置规则按模板(db_type + db_version)匹配实例，自定义规则直接绑定 instance_id。
 * <p>协作类：{@link AlertEventLifecycleService}（窗口/建单/恢复）、{@link AlertCustomSqlEvaluator}（自定义 SQL）、
 * {@code AlertMessageRenderer}（消息渲染，经 lifecycleService 间接使用）。
 */
@Component
public class AlertEvaluateJobHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluateJobHandler.class);

    /** 查询最新指标值的时间窗口（分钟），防止采集延迟导致误判。 */
    private static final int METRIC_WINDOW_MINUTES = 5;

    @Resource
    private AlertRuleMapper alertRuleMapper;
    @Resource
    private AlertEventMapper alertEventMapper;
    @Resource
    private AlertEvaluateWindowMapper alertEvaluateWindowMapper;
    @Resource
    private AlertRuleInstanceConfigMapper instanceConfigMapper;
    @Resource
    private DbInstanceMapper dbInstanceMapper;
    @Resource
    private MetricDefinitionMapper metricDefinitionMapper;
    @Resource
    private MetricValueQueryDao metricValueQueryDao;
    @Resource
    private AlertEventLifecycleService lifecycleService;
    @Resource
    private AlertCustomSqlEvaluator customSqlEvaluator;
    @Resource
    private InstanceService instanceService;
    @Resource
    private AlertEventOperateLogMapper operateLogMapper;
    @Resource
    private MonitorScenarioMapper scenarioMapper;
    @Resource
    private ScenarioInstanceConfigMapper scenarioConfigMapper;
    @Resource
    private ScenarioEvaluationService scenarioEvaluationService;
    @Resource
    private DatabaseTypeMapper databaseTypeMapper;

    /**
     * HOST 内置类型的 database_type.id 缓存（内置数据，进程生命周期内不变）。
     * host.* 规则的 db_type_id 指向该记录，实例匹配走「关联了主机的实例」专用分支。
     */
    private volatile Long hostDbTypeId;
    private volatile boolean hostDbTypeLoaded;

    /** 是否启用挂起事件自愈兜底。 */
    @org.springframework.beans.factory.annotation.Value("${alert.evaluate.self-heal-enabled:true}")
    private boolean selfHealEnabled;
    /** 事件处于 metric_missing（指标缺失）或 sql_error（自定义 SQL 评估失败）状态超过该分钟数后自动恢复。 */
    @org.springframework.beans.factory.annotation.Value("${alert.evaluate.metric-missing-recover-minutes:30}")
    private int metricMissingRecoverMinutes;
    /**
     * 指标缺失兜底告警开关（默认关闭）。开启后：当某规则的指标在评估窗口内无新鲜数据、
     * 且当前不存在对应活跃事件时，主动建单（eval_state=metric_missing），使"采集长期中断"可被感知，
     * 而不再是静默无告警。数据恢复后按规则恢复条件自然恢复，或由自愈兜底在超时后自动恢复。
     */
    @org.springframework.beans.factory.annotation.Value("${alert.evaluate.alert-on-metric-missing:false}")
    private boolean alertOnMetricMissing;

    @XxlJob("alertEvaluateJobHandler")
    public void evaluate() {
        int shardIndex = safeShardIndex();
        int shardTotal = safeShardTotal();
        // 过期窗口清理与挂起事件自愈都是"全库级"维护操作（各分片执行结果一致），
        // 仅由 0 号分片执行一次即可，避免每个分片每分钟重复全表扫描活跃事件/窗口，显著降低 DB 压力。
        if (shardIndex == 0) {
            cleanupEvaluateState();
            selfHealStuckEvents();
        }
        List<AlertRule> builtinTemplates = alertRuleMapper.selectList(null);
        builtinTemplates.forEach(r -> r.setRuleType("builtin"));
        List<AlertRuleInstanceConfig> customConfigs = instanceConfigMapper.selectList(
                new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                        .eq(AlertRuleInstanceConfig::getRuleType, "custom")
                        .eq(AlertRuleInstanceConfig::getEnabled, true));
        if (builtinTemplates.isEmpty() && customConfigs.isEmpty()) {
            XxlJobHelper.log("无启用告警规则，跳过本轮评估");
            return;
        }
        XxlJobHelper.log("本轮评估：内置模板 {} 条，自定义实例规则 {} 条", builtinTemplates.size(), customConfigs.size());

        List<CollectTargetVo> shardTargets = instanceService.listByShard(shardIndex, shardTotal);
        Map<Long, CollectTargetVo> targetMap = shardTargets.stream()
                .filter(t -> t.getId() != null)
                .collect(java.util.stream.Collectors.toMap(CollectTargetVo::getId, t -> t, (a, b) -> a));
        List<Long> shardInstanceIds = targetMap.keySet().stream().toList();
        if (shardInstanceIds.isEmpty()) {
            XxlJobHelper.log("分片 {}/{} 无待评估实例，跳过本轮评估", shardIndex, shardTotal);
            return;
        }

        // 评估当前分片内所有非暂停实例（normal + abnormal），paused 实例不采集、也不告警
        List<DbInstance> allInstances = dbInstanceMapper.selectList(
                new LambdaQueryWrapper<DbInstance>()
                        .in(DbInstance::getId, shardInstanceIds)
                        .ne(DbInstance::getStatus, InstanceStatus.PAUSED));
        List<AlertRule> customRules = customConfigs.stream().map(this::materializeCustomRule).toList();
        List<AlertRule> allRulesForFreq = new ArrayList<>(builtinTemplates);
        allRulesForFreq.addAll(customRules);
        Map<String, String> metricFrequencyMap = loadMetricFrequencyMap(allRulesForFreq);
        // 批量预载本轮所需状态（实例级配置/活跃事件/持续窗口/静默键），避免规则×实例逐对查库
        AlertEvalContext ctx = buildEvalContext(shardInstanceIds);
        long epochMinute = OffsetDateTime.now().toEpochSecond() / 60;
        XxlJobHelper.log("分片 {}/{} 本轮待评估非暂停实例数: {}", shardIndex, shardTotal, allInstances.size());

        // 展开为统一的 (规则 × 实例) 评估对，builtin/custom 共用同一条评估路径
        List<RuleInstancePair> pairs = new ArrayList<>();
        for (AlertRule rule : builtinTemplates) {
            for (Long instanceId : resolveInstanceIds(rule, allInstances)) {
                AlertRuleInstanceConfig cfg = ctx.builtinConfig(rule.getRuleCode(), instanceId);
                // 内置规则默认停用：无实例配置记录即不评估
                if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
                    continue;
                }
                pairs.add(new RuleInstancePair(materializeRule(rule, cfg), instanceId));
            }
        }
        for (AlertRuleInstanceConfig customCfg : customConfigs) {
            Long instanceId = customCfg.getInstanceId();
            if (instanceId == null || allInstances.stream().noneMatch(i -> instanceId.equals(i.getId()))) {
                continue;
            }
            pairs.add(new RuleInstancePair(materializeCustomRule(customCfg), instanceId));
        }

        AlertEvalCounter total = AlertEvalCounter.EMPTY;
        for (RuleInstancePair pair : pairs) {
            try {
                total = merge(total, evaluateOneRuleInstance(
                        ctx, pair.rule(), pair.instanceId(), allInstances, targetMap, metricFrequencyMap, epochMinute));
            } catch (Exception e) {
                log.error("告警规则 [{}] 实例 [{}] 评估异常", pair.rule().getRuleCode(), pair.instanceId(), e);
            }
        }

        // 场景综合诊断评估（多信号 AND/OR，触发生成综合事件，复用同一上下文与生命周期）
        total = merge(total, evaluateScenarios(ctx, allInstances, epochMinute));

        XxlJobHelper.log("本轮评估完成：触发 {} 次，通知 {} 次，静默抑制 {} 次，恢复 {} 次",
                total.triggered(), total.notified(), total.silenced(), total.recovered());
    }

    // ── 场景综合诊断评估 ─────────────────────────────────────────────────────

    /**
     * 场景评估段：加载启用的 (场景 × 实例) 对，多信号取数 + 条件组树三值求值。
     * <ul>
     *   <li>TRUE → 生成/归并综合事件（event_source=scenario，携带信号快照与诊断结论）；</li>
     *   <li>FALSE → 有活跃事件则走恢复（含恢复持续窗口）；</li>
     *   <li>UNKNOWN（信号数据缺失无法确认）→ 复用 metric_missing 标记与自愈兜底，不触发也不恢复。</li>
     * </ul>
     */
    private AlertEvalCounter evaluateScenarios(AlertEvalContext ctx, List<DbInstance> allInstances, long epochMinute) {
        AlertEvalCounter total = AlertEvalCounter.EMPTY;
        List<MonitorScenario> scenarios;
        try {
            scenarios = scenarioMapper.selectList(null);
        } catch (Exception e) {
            log.warn("加载场景模板失败，跳过本轮场景评估: {}", e.getMessage());
            return total;
        }
        if (scenarios.isEmpty() || allInstances.isEmpty()) {
            return total;
        }
        // 分片实例的启用配置批量预载（场景默认停用：无配置或 enabled=false 不评估）
        List<Long> instanceIds = allInstances.stream().map(DbInstance::getId).toList();
        Map<String, ScenarioInstanceConfig> enabledConfigs = new HashMap<>();
        scenarioConfigMapper.selectList(new LambdaQueryWrapper<ScenarioInstanceConfig>()
                        .in(ScenarioInstanceConfig::getInstanceId, instanceIds)
                        .eq(ScenarioInstanceConfig::getEnabled, true))
                .forEach(c -> enabledConfigs.putIfAbsent(c.getScenarioCode() + "|" + c.getInstanceId(), c));
        if (enabledConfigs.isEmpty()) {
            return total;
        }

        int evaluated = 0;
        for (MonitorScenario scenario : scenarios) {
            AlertRule pseudoRule = materializeScenarioRule(scenario);
            for (Long instanceId : resolveInstanceIds(pseudoRule, allInstances)) {
                ScenarioInstanceConfig cfg = enabledConfigs.get(scenario.getScenarioCode() + "|" + instanceId);
                if (cfg == null) {
                    continue;
                }
                if (!shouldRunNow(pseudoRule, instanceId, epochMinute)) {
                    continue;
                }
                try {
                    total = merge(total, evaluateOneScenarioInstance(ctx, scenario, pseudoRule, instanceId,
                            cfg.getConditionOverrides(), allInstances));
                    evaluated++;
                } catch (Exception e) {
                    log.error("场景 [{}] 实例 [{}] 评估异常", scenario.getScenarioCode(), instanceId, e);
                }
            }
        }
        if (evaluated > 0) {
            XxlJobHelper.log("场景评估：本轮评估 {} 个 (场景×实例) 对", evaluated);
        }
        return total;
    }

    private AlertEvalCounter evaluateOneScenarioInstance(AlertEvalContext ctx, MonitorScenario scenario,
                                                         AlertRule pseudoRule, Long instanceId,
                                                         Map<String, Object> conditionOverrides,
                                                         List<DbInstance> allInstances) {
        String instanceName = findName(allInstances, instanceId);
        ScenarioEvaluationService.ScenarioEvalResult result =
                scenarioEvaluationService.evaluate(scenario, instanceId, conditionOverrides);
        switch (result.state()) {
            case TRUE -> {
                long metCount = result.signals().stream()
                        .filter(s -> s.state() == ScenarioConditionEvaluator.TriState.TRUE)
                        .count();
                AlertEventLifecycleService.ScenarioEventPayload payload =
                        new AlertEventLifecycleService.ScenarioEventPayload(
                                scenario.getScenarioCode(), result.diagnosis(), toSnapshot(result.signals()));
                AlertUpsertResult upsert = lifecycleService.handleTriggerWithWindow(
                        ctx, pseudoRule, instanceId, instanceName, null, metCount, payload);
                if (upsert.windowPending() || upsert.disposedSkipped()) {
                    return AlertEvalCounter.EMPTY;
                }
                return upsert.silenceSuppressed()
                        ? AlertEvalCounter.EMPTY.addSilenced()
                        : AlertEvalCounter.EMPTY.addTriggered(upsert.notified());
            }
            case FALSE -> {
                boolean hasActive = ctx.activeEvent(
                        AlertConditionEvaluator.buildDedupKey(scenario.getScenarioCode(), instanceId, null)) != null;
                if (hasActive) {
                    return lifecycleService.handleRecoverWithWindow(ctx, pseudoRule, instanceId, instanceName, null, 0)
                            ? AlertEvalCounter.EMPTY.addRecovered()
                            : AlertEvalCounter.EMPTY;
                }
                lifecycleService.clearTriggerPending(ctx, pseudoRule, instanceId, null);
                return AlertEvalCounter.EMPTY;
            }
            default -> {
                // UNKNOWN：信号数据缺失，复用 metric_missing 标记（不做兜底建单，采集中断由规则侧感知）
                lifecycleService.markMetricMissing(ctx, pseudoRule, instanceId, instanceName, null, false);
                return AlertEvalCounter.EMPTY;
            }
        }
    }

    /** 场景 → 伪规则：复用事件生命周期（dedup_key=scenario_code:instanceId、持续窗口、静默、通知）。 */
    private AlertRule materializeScenarioRule(MonitorScenario scenario) {
        AlertRule rule = new AlertRule();
        rule.setId(stableIdFromRuleCode(scenario.getScenarioCode()));
        rule.setRuleCode(scenario.getScenarioCode());
        rule.setRuleName(scenario.getScenarioName());
        rule.setRuleType(AlertEventLifecycleService.RULE_TYPE_SCENARIO);
        rule.setRuleLevel(scenario.getSeverity());
        rule.setDbTypeId(scenario.getDbTypeId());
        rule.setDbVersionIds(scenario.getDbVersionIds());
        rule.setDescription(scenario.getDescription());
        // metricName 仅用于 metric_missing 提示文案（场景涉及的全部指标编码）
        List<String> metricCodes = ScenarioConditionEvaluator.collectConditions(scenario.getConditionConfig()).stream()
                .map(c -> c.get("metricCode"))
                .filter(m -> m instanceof String s && !s.isBlank())
                .map(String.class::cast)
                .distinct()
                .toList();
        rule.setMetricName(String.join(",", metricCodes));
        rule.setConditionConfig(scenario.getConditionConfig());
        rule.setRecoveryConfig(scenario.getRecoveryConfig());
        rule.setNotificationConfig(scenario.getNotificationConfig());
        rule.setScanIntervalMin(scenario.getScanIntervalMin());
        return rule;
    }

    /** 信号求值明细 → 事件快照 JSON 结构（[{"code","name","expr","currentVal","met","state"}]）。 */
    private List<Map<String, Object>> toSnapshot(List<ScenarioConditionEvaluator.SignalStatus> signals) {
        List<Map<String, Object>> snapshot = new ArrayList<>(signals.size());
        for (ScenarioConditionEvaluator.SignalStatus s : signals) {
            Map<String, Object> item = new HashMap<>();
            item.put("code", s.code());
            item.put("name", s.name());
            item.put("expr", s.exprText());
            item.put("metricCode", s.metricCode());
            item.put("currentVal", formatSignalValue(s.currentValue(), s.unit()));
            item.put("met", s.state() == ScenarioConditionEvaluator.TriState.TRUE);
            item.put("state", switch (s.state()) {
                case TRUE -> "met";
                case FALSE -> "normal";
                case UNKNOWN -> "unknown";
            });
            snapshot.add(item);
        }
        return snapshot;
    }

    private static String formatSignalValue(Double value, String unit) {
        if (value == null) {
            return "-";
        }
        double rounded = Math.round(value * 100.0) / 100.0;
        String text = rounded == Math.floor(rounded) && !Double.isInfinite(rounded)
                ? String.valueOf((long) rounded)
                : String.valueOf(rounded);
        return unit == null || unit.isBlank() ? text : text + unit;
    }

    /** builtin 与 custom 共用的单 (规则 × 实例) 评估路径。 */
    private AlertEvalCounter evaluateOneRuleInstance(AlertEvalContext ctx, AlertRule rule, Long instanceId,
                                                     List<DbInstance> allInstances, Map<Long, CollectTargetVo> targetMap,
                                                     Map<String, String> metricFrequencyMap, long epochMinute) {
        if (!shouldRunNow(rule, instanceId, epochMinute)) {
            return AlertEvalCounter.EMPTY;
        }
        String instanceName = findName(allInstances, instanceId);
        if (AlertCustomSqlEvaluator.hasCustomSql(rule)) {
            return customSqlEvaluator.evaluate(ctx, rule, instanceId, instanceName, targetMap.get(instanceId));
        }
        EvalResult result = evaluateRule(ctx, rule, instanceId, metricFrequencyMap);
        if (result.isTrigger()) {
            AlertUpsertResult upsert = lifecycleService.handleTriggerWithWindow(
                    ctx, rule, instanceId, instanceName, null, result.triggerValue());
            if (upsert.windowPending() || upsert.disposedSkipped()) {
                return AlertEvalCounter.EMPTY;
            }
            return upsert.silenceSuppressed()
                    ? AlertEvalCounter.EMPTY.addSilenced()
                    : AlertEvalCounter.EMPTY.addTriggered(upsert.notified());
        }
        if (result.isRecover()) {
            return lifecycleService.handleRecoverWithWindow(ctx, rule, instanceId, instanceName, null, result.triggerValue())
                    ? AlertEvalCounter.EMPTY.addRecovered()
                    : AlertEvalCounter.EMPTY;
        }
        if (result.isMetricMissing()) {
            return lifecycleService.markMetricMissing(ctx, rule, instanceId, instanceName, null, alertOnMetricMissing)
                    ? AlertEvalCounter.EMPTY.addTriggered(false)
                    : AlertEvalCounter.EMPTY;
        }
        lifecycleService.clearTriggerPending(ctx, rule, instanceId, null);
        return AlertEvalCounter.EMPTY;
    }

    private static AlertEvalCounter merge(AlertEvalCounter a, AlertEvalCounter b) {
        return new AlertEvalCounter(a.triggered() + b.triggered(), a.silenced() + b.silenced(),
                a.notified() + b.notified(), a.recovered() + b.recovered());
    }

    private record RuleInstancePair(AlertRule rule, Long instanceId) {}

    // ── 评估轮次上下文（批量预载，消除规则×实例逐对查库） ─────────────────────

    private AlertEvalContext buildEvalContext(List<Long> shardInstanceIds) {
        Map<String, AlertRuleInstanceConfig> builtinConfigs = new HashMap<>();
        instanceConfigMapper.selectList(new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                        .eq(AlertRuleInstanceConfig::getRuleType, "builtin")
                        .in(AlertRuleInstanceConfig::getInstanceId, shardInstanceIds))
                .forEach(c -> builtinConfigs.putIfAbsent(
                        AlertEvalContext.builtinConfigKey(c.getRuleCode(), c.getInstanceId()), c));

        Map<String, AlertEvent> activeEvents = new HashMap<>();
        alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                        .in(AlertEvent::getInstanceId, shardInstanceIds)
                        .in(AlertEvent::getStatus, "pending", "confirmed", "handling"))
                .forEach(e -> {
                    if (e.getDedupKey() != null) {
                        activeEvents.putIfAbsent(e.getDedupKey(), e);
                    }
                });

        // 持续窗口表只存"正在等待建单/恢复"的活跃窗口，行数天然很小（目标规模 ≤1000 实例），
        // 直接全量预载 + 内存索引即可，无需按分片正则过滤（正则无法走索引，反而更慢、更复杂）。
        Map<String, AlertEvaluateWindow> windows = new HashMap<>();
        alertEvaluateWindowMapper.selectAll()
                .forEach(w -> windows.put(AlertEvalContext.windowKey(w.getDedupKey(), w.getWindowType()), w));

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, OffsetDateTime> silenceUntil = new HashMap<>();
        alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                        .select(AlertEvent::getDedupKey, AlertEvent::getSilenceUntilTime)
                        .in(AlertEvent::getInstanceId, shardInstanceIds)
                        .in(AlertEvent::getStatus, "ignored", "closed")
                        .isNotNull(AlertEvent::getSilenceUntilTime)
                        .gt(AlertEvent::getSilenceUntilTime, now))
                .forEach(e -> {
                    if (e.getDedupKey() != null) {
                        silenceUntil.merge(e.getDedupKey(), e.getSilenceUntilTime(),
                                (a, b) -> a.isAfter(b) ? a : b);
                    }
                });
        return new AlertEvalContext(builtinConfigs, activeEvents, windows, silenceUntil);
    }

    // ── 范围解析 ────────────────────────────────────────────────────────────────

    private List<Long> resolveInstanceIds(AlertRule rule, List<DbInstance> allInstances) {
        Long ruleDbTypeId = rule.getDbTypeId();
        if (ruleDbTypeId == null) {
            return allInstances.stream().map(DbInstance::getId).toList();
        }
        // HOST 类型规则（host.* 主机指标）：匹配所有关联了主机的实例，跳过数据库类型/版本过滤
        if (ruleDbTypeId.equals(resolveHostDbTypeId())) {
            return allInstances.stream()
                    .filter(i -> i.getHostId() != null)
                    .map(DbInstance::getId)
                    .toList();
        }
        List<Long> ruleVersionIds = rule.getDbVersionIds();
        return allInstances.stream()
                .filter(i -> ruleDbTypeId.equals(i.getDbTypeId()))
                .filter(i -> {
                    if (ruleVersionIds == null || ruleVersionIds.isEmpty()) {
                        return true;
                    }
                    if (i.getDbVersionId() == null) {
                        return true;
                    }
                    return ruleVersionIds.contains(i.getDbVersionId());
                })
                .map(DbInstance::getId)
                .toList();
    }

    /** HOST 内置类型 ID：首次查询后缓存（V133 种子数据，无该记录时返回 null，host 规则不匹配任何实例）。 */
    private Long resolveHostDbTypeId() {
        if (!hostDbTypeLoaded) {
            try {
                DatabaseType hostType = databaseTypeMapper.selectOne(
                        new LambdaQueryWrapper<DatabaseType>()
                                .eq(DatabaseType::getCode, "HOST")
                                .last("LIMIT 1"));
                hostDbTypeId = hostType == null ? null : hostType.getId();
                hostDbTypeLoaded = true;
            } catch (Exception e) {
                log.warn("查询 HOST 数据库类型失败，本轮 host 规则不评估: {}", e.getMessage());
                return null;
            }
        }
        return hostDbTypeId;
    }

    private AlertRule materializeRule(AlertRule template, AlertRuleInstanceConfig cfg) {
        AlertRule merged = new AlertRule();
        merged.setId(template.getId());
        merged.setRuleName(cfg.getRuleName() != null ? cfg.getRuleName() : template.getRuleName());
        merged.setRuleCode(template.getRuleCode());
        merged.setRuleType(template.getRuleType());
        merged.setRuleLevel(cfg.getRuleLevel() != null ? cfg.getRuleLevel() : template.getRuleLevel());
        merged.setDbTypeId(template.getDbTypeId());
        merged.setDbVersionIds(template.getDbVersionIds());
        merged.setDescription(cfg.getDescription() != null ? cfg.getDescription() : template.getDescription());
        merged.setMetricName(cfg.getMetricName() != null ? cfg.getMetricName() : template.getMetricName());
        // 条件/恢复配置按"模板打底 + 实例覆盖逐字段叠加"合并：实例覆盖只含阈值类字段
        // （operator/threshold/unit/duration），整包替换会丢失模板的 customSql/resultMode 等 SQL 评估字段。
        merged.setConditionConfig(overlayConfig(template.getConditionConfig(), cfg.getConditionConfig()));
        merged.setRecoveryConfig(overlayConfig(template.getRecoveryConfig(), cfg.getRecoveryConfig()));
        merged.setNotificationConfig(cfg.getNotificationConfig() != null ? cfg.getNotificationConfig() : template.getNotificationConfig());
        merged.setScanIntervalMin(cfg.getScanIntervalMin() != null ? cfg.getScanIntervalMin() : template.getScanIntervalMin());
        merged.setScanIntervalSource(cfg.getScanIntervalMin() != null ? "USER_OVERRIDE" : template.getScanIntervalSource());
        merged.setCreatedBy(template.getCreatedBy());
        merged.setCreatedAt(template.getCreatedAt());
        merged.setUpdatedAt(template.getUpdatedAt());
        return merged;
    }

    /** 配置叠加合并：模板配置打底，实例覆盖配置逐字段叠加（保留模板独有字段）。 */
    private Map<String, Object> overlayConfig(Map<String, Object> base, Map<String, Object> override) {
        if (override == null || override.isEmpty()) {
            return base;
        }
        if (base == null || base.isEmpty()) {
            return override;
        }
        Map<String, Object> merged = new HashMap<>(base);
        merged.putAll(override);
        return merged;
    }

    private AlertRule materializeCustomRule(AlertRuleInstanceConfig cfg) {
        AlertRule rule = new AlertRule();
        rule.setId(stableIdFromRuleCode(cfg.getRuleCode()));
        rule.setRuleName(cfg.getRuleName());
        rule.setRuleCode(cfg.getRuleCode());
        rule.setRuleType("custom");
        rule.setRuleLevel(cfg.getRuleLevel());
        rule.setDescription(cfg.getDescription());
        rule.setMetricName(cfg.getMetricName());
        rule.setConditionConfig(cfg.getConditionConfig());
        rule.setRecoveryConfig(cfg.getRecoveryConfig());
        rule.setNotificationConfig(cfg.getNotificationConfig());
        rule.setScanIntervalMin(cfg.getScanIntervalMin());
        rule.setScanIntervalSource("USER_OVERRIDE");
        rule.setCreatedAt(cfg.getCreatedAt());
        rule.setUpdatedAt(cfg.getUpdatedAt());
        return rule;
    }

    private long stableIdFromRuleCode(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return 0L;
        }
        long hash = 1125899906842597L;
        String normalized = ruleCode.trim();
        for (int i = 0; i < normalized.length(); i++) {
            hash = 31 * hash + normalized.charAt(i);
        }
        return hash & ((1L << 53) - 1);
    }

    private String findName(List<DbInstance> instances, Long id) {
        return instances.stream()
                .filter(i -> id.equals(i.getId()))
                .map(DbInstance::getName)
                .findFirst()
                .orElse("unknown");
    }

    private boolean shouldRunNow(AlertRule rule, Long instanceId, long epochMinute) {
        int interval = rule.getScanIntervalMin() == null ? 1 : Math.max(1, rule.getScanIntervalMin());
        // 槽位混入 instanceId，避免同一模板对所有实例落在同一分钟评估，打散分钟级负载尖峰
        long ruleId = rule.getId() == null ? 0L : rule.getId();
        long seed = ruleId * 1099511628211L + (instanceId == null ? 0L : instanceId);
        long slot = Math.floorMod(seed, interval);
        return Math.floorMod(epochMinute, interval) == slot;
    }

    private Map<String, String> loadMetricFrequencyMap(List<AlertRule> rules) {
        List<String> metricCodes = rules.stream()
                .map(AlertRule::getMetricName)
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .toList();
        if (metricCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return metricDefinitionMapper.selectList(
                        new LambdaQueryWrapper<MetricDefinition>()
                                .in(MetricDefinition::getMetricCode, metricCodes)
                                .select(MetricDefinition::getMetricCode, MetricDefinition::getFrequency))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        MetricDefinition::getMetricCode,
                        md -> md.getFrequency() == null ? "1m" : md.getFrequency(),
                        (a, b) -> a));
    }

    private String resolveMetricFrequency(String metricCode, Map<String, String> metricFrequencyMap) {
        String frequency = metricFrequencyMap.get(metricCode);
        if (frequency != null) {
            return frequency;
        }
        MetricDefinition def = metricDefinitionMapper.selectOne(
                new LambdaQueryWrapper<MetricDefinition>()
                        .eq(MetricDefinition::getMetricCode, metricCode)
                        .last("LIMIT 1"));
        return def != null && def.getFrequency() != null ? def.getFrequency() : "1m";
    }

    // ── 指标查询 + 条件评估 ───────────────────────────────────────────────────

    private EvalResult evaluateRule(AlertEvalContext ctx, AlertRule rule, Long instanceId, Map<String, String> metricFrequencyMap) {
        String metric = rule.getMetricName();
        if (metric == null || metric.isBlank()) {
            return EvalResult.NORMAL;
        }
        Double latestValue = queryLatestValue(instanceId, metric, resolveMetricFrequency(metric, metricFrequencyMap));
        if (latestValue == null) {
            return EvalResult.metricMissing();
        }

        boolean triggered = AlertConditionEvaluator.checkCondition(rule.getConditionConfig(), latestValue);
        if (triggered) {
            return EvalResult.triggered(latestValue);
        }

        // 检查是否需要恢复：当前有活跃事件且满足 recoveryConfig（留空则以条件反义恢复）
        boolean hasActive = ctx.activeEvent(
                AlertConditionEvaluator.buildDedupKey(rule.getRuleCode(), instanceId, null)) != null;
        if (hasActive) {
            boolean recovered = AlertConditionEvaluator.checkRecovery(
                    rule.getConditionConfig(), rule.getRecoveryConfig(), latestValue);
            if (recovered) {
                return EvalResult.recovered(latestValue);
            }
        }
        return EvalResult.NORMAL;
    }

    /** 按指标频率查询最新值（支持 1m / 1h / 1d，默认 1m）。 */
    private Double queryLatestValue(Long instanceId, String metric, String frequency) {
        try {
            String normalized = frequency == null ? "1m" : frequency.trim().toLowerCase();
            return switch (normalized) {
                case "1h" -> metricValueQueryDao.latest1h(instanceId, metric, 2);
                case "1d" -> metricValueQueryDao.latest1d(instanceId, metric, 2);
                default -> metricValueQueryDao.latest1m(instanceId, metric, METRIC_WINDOW_MINUTES);
            };
        } catch (Exception e) {
            log.warn("查询指标 [{}/{}] 最新值失败: {}", instanceId, metric, e.getMessage());
            return null;
        }
    }

    // ── 维护任务（0 号分片执行） ─────────────────────────────────────────────

    private void cleanupEvaluateState() {
        try {
            alertEvaluateWindowMapper.deleteExpired();
        } catch (Exception e) {
            log.warn("清理告警评估状态失败: {}", e.getMessage());
        }
    }

    /**
     * 挂起事件自愈兜底：修复因指标改名/长期缺失、自定义 SQL 持续失败、实例暂停或删除等原因导致的、
     * 无法通过正常评估恢复的活跃事件。
     * <ol>
     *   <li><b>指标持续缺失</b>：eval_state=metric_missing 且距上次真实触发已超过配置分钟数 → 自动恢复；</li>
     *   <li><b>自定义 SQL 持续失败</b>：eval_state=sql_error 且超时同上 → 自动恢复；</li>
     *   <li><b>实例暂停/删除</b>：活跃事件对应实例已 paused 或在 db_instance 中不存在 → 自动恢复。</li>
     * </ol>
     * 采用幂等 UPDATE，多个 collector 并发执行安全；自愈恢复不发送通知，避免对已删除实例产生噪声。
     */
    private void selfHealStuckEvents() {
        if (!selfHealEnabled) {
            return;
        }
        try {
            int missingRecovered = selfHealStaleEvalState("metric_missing", "指标持续缺失");
            int sqlErrorRecovered = selfHealStaleEvalState("sql_error", "自定义 SQL 持续评估失败");
            int orphanRecovered = selfHealOrphanInstances();
            if (missingRecovered + sqlErrorRecovered + orphanRecovered > 0) {
                log.info("挂起事件自愈：指标缺失恢复 {} 条，SQL 失败恢复 {} 条，实例暂停/删除恢复 {} 条",
                        missingRecovered, sqlErrorRecovered, orphanRecovered);
            }
        } catch (Exception e) {
            log.warn("挂起事件自愈失败: {}", e.getMessage());
        }
    }

    /** 评估异常态（metric_missing / sql_error）持续超时的活跃事件自动恢复。 */
    private int selfHealStaleEvalState(String evalState, String reasonLabel) {
        int minutes = Math.max(1, metricMissingRecoverMinutes);
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(minutes);
        OffsetDateTime now = OffsetDateTime.now();
        // 双重超时条件：
        // - last_trigger_time：评估异常期间不产生新触发，衡量距最后一次真实触发的时长；
        // - last_eval_time：进入异常态时写入一次、异常态期间不再刷新，即"进入异常态的时刻"。
        //   仅凭 last_trigger_time 会误伤扫描间隔大于自愈阈值的规则（如小时级规则的
        //   last_trigger_time 天然陈旧，刚进入 metric_missing 就会被立即恢复）。
        // 先查出待自愈事件，逐事件带守卫 UPDATE（查询与更新之间事件可能被人工处置），
        // 仅对更新成功的事件清理持续窗口（与人工 close/silence 对齐），
        // 避免对仍处于异常态的活跃事件提前删窗。
        List<AlertEvent> stale = alertEventMapper.selectList(
                new LambdaQueryWrapper<AlertEvent>()
                        .select(AlertEvent::getId, AlertEvent::getEventCode,
                                AlertEvent::getStatus, AlertEvent::getDedupKey)
                        .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                        .eq(AlertEvent::getEvalState, evalState)
                        .lt(AlertEvent::getLastTriggerTime, cutoff)
                        .lt(AlertEvent::getLastEvalTime, cutoff));
        if (stale.isEmpty()) {
            return 0;
        }
        List<AlertEvent> healed = new ArrayList<>(stale.size());
        String remark = reasonLabel + "超过 " + minutes + " 分钟，系统自动恢复";
        for (AlertEvent e : stale) {
            int updated = alertEventMapper.update(
                    null,
                    new LambdaUpdateWrapper<AlertEvent>()
                            .eq(AlertEvent::getId, e.getId())
                            .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                            .eq(AlertEvent::getEvalState, evalState)
                            .set(AlertEvent::getStatus, "recovered")
                            .set(AlertEvent::getRecoveryTime, now)
                            .set(AlertEvent::getEvalState, "auto_recovered")
                            .set(AlertEvent::getEvalMessage, remark)
                            // 同步写 last_remark：列表页"最近处置说明"可直接看到自愈原因
                            .set(AlertEvent::getLastRemark, remark)
                            .set(AlertEvent::getLastEvalTime, now));
            if (updated > 0) {
                healed.add(e);
            }
        }
        cleanupWindowsQuietly(healed);
        writeAutoRecoverLogs(healed, remark, now);
        return healed.size();
    }

    /** 实例已暂停或已删除的活跃事件自动恢复。 */
    private int selfHealOrphanInstances() {
        List<AlertEvent> active = alertEventMapper.selectList(
                new LambdaQueryWrapper<AlertEvent>()
                        .select(AlertEvent::getId, AlertEvent::getEventCode, AlertEvent::getStatus,
                                AlertEvent::getInstanceId, AlertEvent::getDedupKey)
                        .in(AlertEvent::getStatus, "pending", "confirmed", "handling"));
        if (active.isEmpty()) {
            return 0;
        }
        Set<Long> instanceIds = active.stream()
                .map(AlertEvent::getInstanceId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (instanceIds.isEmpty()) {
            return 0;
        }
        // 存活（未暂停）实例集合
        Set<Long> aliveIds = dbInstanceMapper.selectList(
                        new LambdaQueryWrapper<DbInstance>()
                                .select(DbInstance::getId)
                                .in(DbInstance::getId, instanceIds)
                                .ne(DbInstance::getStatus, InstanceStatus.PAUSED))
                .stream()
                .map(DbInstance::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<AlertEvent> orphans = active.stream()
                .filter(e -> e.getInstanceId() != null && !aliveIds.contains(e.getInstanceId()))
                .toList();
        if (orphans.isEmpty()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now();
        // 逐事件带守卫 UPDATE，仅对更新成功的事件清窗（同 selfHealStaleEvalState）
        List<AlertEvent> healed = new ArrayList<>(orphans.size());
        for (AlertEvent e : orphans) {
            int updated = alertEventMapper.update(
                    null,
                    new LambdaUpdateWrapper<AlertEvent>()
                            .eq(AlertEvent::getId, e.getId())
                            .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                            .set(AlertEvent::getStatus, "recovered")
                            .set(AlertEvent::getRecoveryTime, now)
                            .set(AlertEvent::getEvalState, "auto_recovered")
                            .set(AlertEvent::getEvalMessage, "实例已暂停或删除，系统自动恢复")
                            .set(AlertEvent::getLastRemark, "实例已暂停或删除，系统自动恢复")
                            .set(AlertEvent::getLastEvalTime, now));
            if (updated > 0) {
                healed.add(e);
            }
        }
        cleanupWindowsQuietly(healed);
        writeAutoRecoverLogs(healed, "实例已暂停或删除，系统自动恢复", now);
        return healed.size();
    }

    /**
     * 自愈成功后补写操作流水（operateType=auto_recover，操作人=系统），
     * 与人工处置/规则停用联动保持审计链完整；写失败仅告警，不影响自愈主流程。
     */
    private void writeAutoRecoverLogs(List<AlertEvent> healed, String remark, OffsetDateTime now) {
        for (AlertEvent e : healed) {
            try {
                AlertEventOperateLog opLog = new AlertEventOperateLog();
                opLog.setEventId(e.getId());
                opLog.setEventCode(e.getEventCode());
                opLog.setOperateType("auto_recover");
                opLog.setFromStatus(e.getStatus());
                opLog.setToStatus("recovered");
                opLog.setOperatorName("系统");
                opLog.setRemark(remark);
                opLog.setCreatedAt(now);
                operateLogMapper.insert(opLog);
            } catch (Exception ex) {
                log.warn("自愈操作流水写入失败 eventId={} err={}", e.getId(), ex.getMessage());
            }
        }
    }

    /** 自愈恢复后清理对应 dedup_key 的持续窗口（与人工 close/silence 对齐）；失败仅告警，窗口有 24h 过期兜底。 */
    private void cleanupWindowsQuietly(List<AlertEvent> events) {
        List<String> keys = events.stream()
                .map(AlertEvent::getDedupKey)
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        try {
            alertEvaluateWindowMapper.deleteByDedupKeys(keys);
        } catch (Exception e) {
            log.warn("自愈清理持续窗口失败: {}", e.getMessage());
        }
    }

    private int safeShardIndex() {
        try {
            return Math.max(0, XxlJobHelper.getShardIndex());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int safeShardTotal() {
        try {
            return Math.max(1, XxlJobHelper.getShardTotal());
        } catch (Exception ignored) {
            return 1;
        }
    }

    // ── 内部结果类型 ──────────────────────────────────────────────────────────

    private static final class EvalResult {
        static final EvalResult NORMAL = new EvalResult("normal", 0);

        private final String type;
        private final double value;

        private EvalResult(String type, double value) {
            this.type = type;
            this.value = value;
        }

        static EvalResult triggered(double v) {
            return new EvalResult("trigger", v);
        }

        static EvalResult recovered(double v) {
            return new EvalResult("recover", v);
        }

        static EvalResult metricMissing() {
            return new EvalResult("missing", 0);
        }

        boolean isTrigger() {
            return "trigger".equals(type);
        }

        boolean isRecover() {
            return "recover".equals(type);
        }

        boolean isMetricMissing() {
            return "missing".equals(type);
        }

        double triggerValue() {
            return value;
        }
    }
}
