package com.lzzh.monitor.collector.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzzh.monitor.collector.config.AlertNotifyProperties;
import com.lzzh.monitor.dao.entity.AlertEvaluateWindow;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.dao.entity.AlertRuleInstanceConfig;
import com.lzzh.monitor.dao.entity.ScenarioInstanceConfig;
import com.lzzh.monitor.dao.mapper.AlertEvaluateWindowMapper;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleInstanceConfigMapper;
import com.lzzh.monitor.dao.mapper.ScenarioInstanceConfigMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警事件生命周期：持续窗口推进、事件建单/更新（upsert）、恢复、指标缺失与 SQL 失败标记。
 * 从 AlertEvaluateJobHandler 拆出，只关心"评估结论 → 事件状态落库 + 通知派发"，不做取数与条件判断。
 */
@Service
public class AlertEventLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AlertEventLifecycleService.class);

    static final String WINDOW_TRIGGER = "trigger";
    static final String WINDOW_RECOVERY = "recovery";
    private static final int WINDOW_EXPIRE_HOURS = 24;

    /** 场景来源事件的伪规则类型标记（rule.ruleType，非持久字段）。 */
    public static final String RULE_TYPE_SCENARIO = "scenario";

    @Resource
    private AlertEventMapper alertEventMapper;
    @Resource
    private AlertEvaluateWindowMapper alertEvaluateWindowMapper;
    @Resource
    private AlertRuleInstanceConfigMapper instanceConfigMapper;
    @Resource
    private ScenarioInstanceConfigMapper scenarioConfigMapper;
    @Resource
    private AlertNotificationService alertNotificationService;
    @Resource
    private AlertMessageRenderer messageRenderer;
    @Resource
    private AlertNotifyProperties notifyProperties;
    @Resource
    private BlockingChainSnapshotService blockingChainSnapshotService;

    /**
     * 场景综合事件载荷：触发时写入事件的来源标识、诊断结论与各信号快照。
     *
     * @param scenarioCode    场景编码（monitor_scenario.scenario_code）
     * @param diagnosis       诊断结论（写入 alert_message）
     * @param signalsSnapshot 信号快照 [{"code","name","expr","currentVal","met"}]
     */
    public record ScenarioEventPayload(String scenarioCode, String diagnosis,
                                       List<Map<String, Object>> signalsSnapshot) {
    }

    // ── 触发 / 恢复（含持续窗口） ────────────────────────────────────────────

    public AlertUpsertResult handleTriggerWithWindow(AlertEvalContext ctx, AlertRule rule, Long instanceId, String instanceName,
                                                     String dimensionKey, double triggerValue) {
        return handleTriggerWithWindow(ctx, rule, instanceId, instanceName, dimensionKey, triggerValue, null);
    }

    /** 触发处理（场景综合事件带 {@link ScenarioEventPayload}，普通规则事件传 null）。 */
    public AlertUpsertResult handleTriggerWithWindow(AlertEvalContext ctx, AlertRule rule, Long instanceId, String instanceName,
                                                     String dimensionKey, double triggerValue, ScenarioEventPayload payload) {
        String dedupKey = buildDedupKey(rule.getRuleCode(), instanceId, dimensionKey);
        AlertEvent existing = ctx.activeEvent(dedupKey);
        int durationSec = AlertConditionEvaluator.readDurationSeconds(rule.getConditionConfig(), "duration");
        OffsetDateTime now = OffsetDateTime.now();
        if (existing != null || durationSec <= 0) {
            deleteWindow(ctx, dedupKey, WINDOW_TRIGGER);
            deleteWindow(ctx, dedupKey, WINDOW_RECOVERY);
            return upsertEvent(ctx, rule, instanceId, instanceName, dimensionKey, triggerValue, existing, payload);
        }
        OffsetDateTime first = touchWindow(ctx, dedupKey, WINDOW_TRIGGER, now);
        if (now.isBefore(first.plusSeconds(durationSec))) {
            return AlertUpsertResult.WINDOW_PENDING;
        }
        deleteWindow(ctx, dedupKey, WINDOW_TRIGGER);
        deleteWindow(ctx, dedupKey, WINDOW_RECOVERY);
        return upsertEvent(ctx, rule, instanceId, instanceName, dimensionKey, triggerValue, null, payload);
    }

    public boolean handleRecoverWithWindow(AlertEvalContext ctx, AlertRule rule, Long instanceId, String instanceName,
                                           String dimensionKey, double currentValue) {
        String dedupKey = buildDedupKey(rule.getRuleCode(), instanceId, dimensionKey);
        AlertEvent active = ctx.activeEvent(dedupKey);
        if (active == null) {
            deleteWindow(ctx, dedupKey, WINDOW_RECOVERY);
            return false;
        }
        int recoveryDuration = AlertConditionEvaluator.readDurationSeconds(rule.getRecoveryConfig(), "duration");
        if (recoveryDuration <= 0) {
            deleteWindow(ctx, dedupKey, WINDOW_TRIGGER);
            deleteWindow(ctx, dedupKey, WINDOW_RECOVERY);
            return recoverEvent(ctx, rule, instanceId, instanceName, dimensionKey, currentValue);
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime first = touchWindow(ctx, dedupKey, WINDOW_RECOVERY, now);
        if (now.isBefore(first.plusSeconds(recoveryDuration))) {
            return false;
        }
        deleteWindow(ctx, dedupKey, WINDOW_TRIGGER);
        deleteWindow(ctx, dedupKey, WINDOW_RECOVERY);
        return recoverEvent(ctx, rule, instanceId, instanceName, dimensionKey, currentValue);
    }

    /**
     * 正常态清理：仅在确有残留状态时才写库（避免每个 规则×实例 每轮都无条件 DELETE+UPDATE）。
     * <p>trigger 与 recovery 两个持续窗口都要重置：正常态（含滞回区间）意味着触发条件与恢复条件
     * 都未连续满足，若只删 trigger 窗口，恢复窗口会带着陈旧的 first_match_time 存活，
     * 值再次进入恢复区间时会被误判为"恢复条件已持续满足"而立即恢复。
     */
    public void clearTriggerPending(AlertEvalContext ctx, AlertRule rule, Long instanceId, String dimensionKey) {
        String dedupKey = buildDedupKey(rule.getRuleCode(), instanceId, dimensionKey);
        deleteWindow(ctx, dedupKey, WINDOW_TRIGGER);
        deleteWindow(ctx, dedupKey, WINDOW_RECOVERY);
        AlertEvent active = ctx.activeEvent(dedupKey);
        if (active != null && isAbnormalEvalState(active.getEvalState())) {
            if (clearEvalState(dedupKey) == 0) {
                // 事件已被人工终态处置，剔除轮内缓存
                ctx.activeEvents().remove(dedupKey);
                return;
            }
            active.setEvalState("normal");
            active.setEvalMessage(null);
        }
    }

    // ── 指标缺失 / SQL 失败 ──────────────────────────────────────────────────

    /**
     * 标记指标缺失。
     * <ul>
     *   <li>已有活跃事件：更新为 metric_missing，保留告警状态（不新建）；</li>
     *   <li>无活跃事件且 {@code alertOnMetricMissing=true}：兜底建单，使采集中断可被感知。</li>
     * </ul>
     *
     * @return 是否新建了一条兜底告警事件（用于统计计数）
     */
    public boolean markMetricMissing(AlertEvalContext ctx, AlertRule rule, Long instanceId, String instanceName,
                                     String dimensionKey, boolean alertOnMetricMissing) {
        String dedupKey = buildDedupKey(rule.getRuleCode(), instanceId, dimensionKey);
        // 指标缺失打断了触发/恢复条件的连续观测，两个持续窗口都必须重置，
        // 否则数据恢复后的首个触发样本会拿着缺失前的 first_match_time 立即建单/恢复
        deleteWindow(ctx, dedupKey, WINDOW_TRIGGER);
        deleteWindow(ctx, dedupKey, WINDOW_RECOVERY);
        AlertEvent active = ctx.activeEvent(dedupKey);
        if (active == null) {
            return alertOnMetricMissing && createMetricMissingEvent(ctx, rule, instanceId, instanceName, dimensionKey, dedupKey);
        }
        if ("metric_missing".equals(active.getEvalState())) {
            return false;
        }
        // 规则已在评估期间停用时不再写 eval 状态（停用联动关闭会处理该事件），保持"停用即止噪"语义
        if (!isRuleStillEnabled(rule, instanceId)) {
            ctx.activeEvents().remove(dedupKey);
            return false;
        }
        String message = "指标 " + nullSafe(rule.getMetricName()) + " 在当前评估窗口内无新鲜数据，保留告警状态";
        int updated = alertEventMapper.update(
                null,
                new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getDedupKey, dedupKey)
                        .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                        .set(AlertEvent::getEvalState, "metric_missing")
                        .set(AlertEvent::getEvalMessage, message)
                        .set(AlertEvent::getLastEvalTime, OffsetDateTime.now())
        );
        if (updated == 0) {
            // 事件在本轮评估期间已被人工终态处置，剔除轮内缓存，本轮不再基于陈旧快照操作
            ctx.activeEvents().remove(dedupKey);
            return false;
        }
        active.setEvalState("metric_missing");
        active.setEvalMessage(message);
        return false;
    }

    /**
     * 标记自定义 SQL 评估失败（建连失败/SQL 报错）：为该规则在此实例下的所有活跃事件写入
     * {@code eval_state=sql_error}，保留告警状态并在界面上可见失败原因；SQL 持续失败超时后由自愈兜底自动恢复。
     */
    public void markSqlError(AlertEvalContext ctx, AlertRule rule, Long instanceId, String errorMessage) {
        List<AlertEvent> actives = listActiveEvents(ctx, rule, instanceId);
        if (actives.isEmpty()) {
            return;
        }
        // 规则已在评估期间停用时不再写 eval 状态（停用联动关闭会处理这些事件）。
        // 仅在确有事件需要流转到 sql_error 时才回查，持续失败的后续轮次不产生额外点查
        boolean needsTransition = actives.stream().anyMatch(a -> !"sql_error".equals(a.getEvalState()));
        if (needsTransition && !isRuleStillEnabled(rule, instanceId)) {
            actives.forEach(a -> ctx.activeEvents().remove(a.getDedupKey()));
            return;
        }
        String message = truncate("自定义 SQL 评估失败，保留告警状态：" + nullSafe(errorMessage), 500);
        OffsetDateTime now = OffsetDateTime.now();
        for (AlertEvent active : actives) {
            // SQL 失败打断连续观测，与 metric_missing 同理重置持续窗口
            deleteWindow(ctx, active.getDedupKey(), WINDOW_TRIGGER);
            deleteWindow(ctx, active.getDedupKey(), WINDOW_RECOVERY);
            if ("sql_error".equals(active.getEvalState())) {
                continue;
            }
            int updated = alertEventMapper.update(
                    null,
                    new LambdaUpdateWrapper<AlertEvent>()
                            .eq(AlertEvent::getDedupKey, active.getDedupKey())
                            .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                            .set(AlertEvent::getEvalState, "sql_error")
                            .set(AlertEvent::getEvalMessage, message)
                            .set(AlertEvent::getLastEvalTime, now));
            if (updated == 0) {
                // 事件已被人工终态处置，剔除轮内缓存
                ctx.activeEvents().remove(active.getDedupKey());
                continue;
            }
            active.setEvalState("sql_error");
            active.setEvalMessage(message);
        }
    }

    /** 该规则在此实例下的全部活跃事件（精确 dedupKey + 维度前缀）。 */
    public List<AlertEvent> listActiveEvents(AlertEvalContext ctx, AlertRule rule, Long instanceId) {
        String prefix = nullSafe(rule.getRuleCode()) + ":" + instanceId + ":";
        String exact = nullSafe(rule.getRuleCode()) + ":" + instanceId;
        return ctx.activeEvents().values().stream()
                .filter(e -> exact.equals(e.getDedupKey())
                        || (e.getDedupKey() != null && e.getDedupKey().startsWith(prefix)))
                .toList();
    }

    // ── 事件 upsert / 恢复 ───────────────────────────────────────────────────

    private AlertUpsertResult upsertEvent(AlertEvalContext ctx, AlertRule rule, Long instanceId, String instanceName,
                                          String dimensionKey, double triggerValue, AlertEvent existing,
                                          ScenarioEventPayload payload) {
        String dedupKey = buildDedupKey(rule.getRuleCode(), instanceId, dimensionKey);
        if (isInSilenceWindow(ctx, dedupKey)) {
            return AlertUpsertResult.SILENCE_SUPPRESSED;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String triggerValueStr = formatValue(triggerValue);

        if (existing == null) {
            existing = ctx.activeEvent(dedupKey);
        }

        if (existing != null) {
            // 已有活跃事件：累计触发次数，更新最近触发时间与触发值。
            // 更新带 status 守卫：轮内快照可能陈旧（评估期间用户在 admin 侧关闭/静默了事件），
            // 不加守卫会改写已终态事件的触发字段、甚至为其派发通知。
            int nextTriggerCount = (existing.getTriggerCount() == null ? 0 : existing.getTriggerCount()) + 1;
            String threshold = extractThreshold(rule.getConditionConfig());
            // 场景综合事件：alert_message 直接使用诊断结论，不走规则消息模板
            String alertMessage = payload != null
                    ? nullSafe(payload.diagnosis())
                    : messageRenderer.render(
                    rule,
                    AlertMessageRenderer.MessagePhase.TRIGGER,
                    existing.getEventCode(),
                    existing.getStatus(),
                    instanceName,
                    existing.getDimensionKey(),
                    triggerValueStr,
                    threshold,
                    existing.getTriggerTime(),
                    now,
                    nextTriggerCount
            );
            LambdaUpdateWrapper<AlertEvent> updateWrapper = new LambdaUpdateWrapper<AlertEvent>()
                    .eq(AlertEvent::getId, existing.getId())
                    .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                    .set(AlertEvent::getTriggerCount, nextTriggerCount)
                    .set(AlertEvent::getLastTriggerTime, now)
                    .set(AlertEvent::getTriggerValue, triggerValueStr)
                    .set(AlertEvent::getEvalState, "normal")
                    .set(AlertEvent::getEvalMessage, null)
                    .set(AlertEvent::getLastEvalTime, now)
                    .set(AlertEvent::getThresholdValue, threshold)
                    .set(AlertEvent::getAlertMessage, alertMessage);
            if (payload != null) {
                // 归并触发时刷新信号快照：同一场景不同轮次可能由不同信号组合命中
                updateWrapper.set(AlertEvent::getSignalsSnapshot, toJson(payload.signalsSnapshot()));
            }
            int updated = alertEventMapper.update(null, updateWrapper);
            if (updated == 0) {
                // 事件在本轮评估期间已被人工关闭/静默或规则停用联动关闭：
                // 剔除轮内缓存并跳过本轮（不新建、不通知），下一轮以 DB 为准重新评估
                ctx.activeEvents().remove(dedupKey);
                log.info("事件已在评估期间被终态处置，跳过本轮更新 dedupKey={}", dedupKey);
                return AlertUpsertResult.DISPOSED_SKIPPED;
            }
            // 注意："已派发"≠"已送达"：notifyOnTrigger 仅表示通知记录已落库并提交异步发送/重试队列，
            // 真实送达结果以 alert_notify_record.status 为准，此处 dispatched 仅用于驱动
            // lastNotifyTime/notifyCount（静默期判断）与结果分支，不代表下游确认成功。
            boolean dispatched = false;
            if (shouldSendTriggerNotification(rule, existing.getLastNotifyTime(), now)) {
                AlertEvent snapshot = buildNotifySnapshot(existing, triggerValueStr, threshold, nextTriggerCount, now, alertMessage);
                if (alertNotificationService.notifyOnTrigger(rule, snapshot)) {
                    int nextNotifyCount = (existing.getNotifyCount() == null ? 0 : existing.getNotifyCount()) + 1;
                    // 通知字段二次更新同样带 status 守卫：主更新与此处之间事件仍可能被人工处置，
                    // 不守卫会向终态事件写入通知字段（通知本身已派发，无法撤回，但字段不再污染终态行）。
                    // dispatched 与落库行数对齐：DB 未更新（已被终态处置）时内存快照不更新、
                    // 也不计入"已通知"统计，避免统计虚高与静默期判定口径漂移
                    if (updateNotifyFields(existing.getId(), now, nextNotifyCount) > 0) {
                        existing.setLastNotifyTime(now);
                        existing.setNotifyCount(nextNotifyCount);
                        dispatched = true;
                    }
                }
            }
            // 同步轮内快照，保证同轮后续判断读到最新状态
            existing.setTriggerCount(nextTriggerCount);
            existing.setLastTriggerTime(now);
            existing.setTriggerValue(triggerValueStr);
            existing.setThresholdValue(threshold);
            existing.setEvalState("normal");
            existing.setEvalMessage(null);
            return dispatched ? AlertUpsertResult.TRIGGERED_AND_NOTIFIED : AlertUpsertResult.TRIGGERED_ONLY;
        } else {
            // 建单前回查规则启用状态：(规则×实例) 列表在轮初构建，评估期间规则可能被停用/删除，
            // 若无此校验，"停用规则止噪"后同轮仍可能新建一条告警。建单是低频动作，点查代价可忽略
            if (!isRuleStillEnabled(rule, instanceId)) {
                log.info("规则已在评估期间停用，跳过建单 rule=[{}] instance=[{}]", rule.getRuleCode(), instanceId);
                return AlertUpsertResult.DISPOSED_SKIPPED;
            }
            // 新建告警事件
            AlertEvent event = new AlertEvent();
            event.setEventCode(newEventCode());
            event.setRuleId(rule.getId());
            event.setRuleName(rule.getRuleName());
            event.setRuleLevel(rule.getRuleLevel());
            event.setInstanceId(instanceId);
            event.setInstanceName(instanceName);
            event.setTriggerValue(triggerValueStr);
            String threshold = extractThreshold(rule.getConditionConfig());
            event.setThresholdValue(threshold);
            event.setDimensionKey(dimensionKey);
            event.setDedupKey(dedupKey);
            event.setTriggerCount(1);
            event.setTriggerTime(now);
            event.setLastTriggerTime(now);
            event.setStatus("pending");
            if (payload != null) {
                // 场景综合事件：rule_id 无对应 alert_rule 行置空，来源/场景编码/信号快照/诊断结论落库
                event.setRuleId(null);
                event.setEventSource("scenario");
                event.setScenarioCode(payload.scenarioCode());
                event.setSignalsSnapshot(payload.signalsSnapshot());
                event.setAlertMessage(nullSafe(payload.diagnosis()));
            } else {
                event.setEventSource(RULE_TYPE_SCENARIO.equals(rule.getRuleType()) ? "scenario" : "rule");
                event.setAlertMessage(messageRenderer.render(
                        rule,
                        AlertMessageRenderer.MessagePhase.TRIGGER,
                        event.getEventCode(),
                        event.getStatus(),
                        instanceName,
                        dimensionKey,
                        triggerValueStr,
                        threshold,
                        now,
                        now,
                        1
                ));
            }
            event.setNotifyCount(0);
            event.setEvalState("normal");
            event.setLastEvalTime(now);
            try {
                alertEventMapper.insert(event);
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                // 并发建单冲突：多分片/多节点同一评估周期内几乎同时判定同一 dedupKey 满足触发条件时，
                // 触发 uk_alert_event_active_dedup 部分唯一索引冲突。此处不视为异常，回退为
                // "更新已有活跃事件" 分支，保证触发次数/通知不丢失，避免任务因唯一约束异常而中断。
                log.info("并发建单冲突，回退为更新已有活跃事件 dedupKey={}", dedupKey);
                AlertEvent concurrent = alertEventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                        .eq(AlertEvent::getDedupKey, dedupKey)
                        .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                        .last("LIMIT 1"));
                if (concurrent == null) {
                    // 极端时序：并发事件此刻已恢复/关闭，dedupKey 已释放，本轮放弃，下一轮重新评估
                    log.warn("并发建单冲突后未查到活跃事件，本轮放弃 dedupKey={}", dedupKey);
                    return AlertUpsertResult.DISPOSED_SKIPPED;
                }
                ctx.activeEvents().put(dedupKey, concurrent);
                return upsertEvent(ctx, rule, instanceId, instanceName, dimensionKey, triggerValue, concurrent, payload);
            }
            ctx.activeEvents().put(dedupKey, event);
            // 锁相关事件建单成功后异步抓取阻塞链现场快照（仅首次建单，归并触发不重复抓取）
            if (BlockingChainSnapshotService.isLockRelated(
                    rule.getMetricName(), payload != null ? payload.scenarioCode() : null)) {
                blockingChainSnapshotService.captureAsync(event.getId(), instanceId);
            }
            if (payload != null) {
                // 累加场景实例触发次数（列表页"触发次数"列），失败不影响事件主流程
                try {
                    scenarioConfigMapper.incrementTriggerCount(payload.scenarioCode(), instanceId);
                } catch (Exception e) {
                    log.warn("场景触发次数累加失败 scenario=[{}] instance=[{}]: {}",
                            payload.scenarioCode(), instanceId, e.getMessage());
                }
            }
            log.info("告警事件建单 rule=[{}] instance=[{}] value={}", rule.getRuleCode(), instanceId, triggerValueStr);
            if (shouldSendTriggerNotification(rule, null, now)
                    && alertNotificationService.notifyOnTrigger(rule, event)) {
                // "已通知"统计与通知字段落库行数对齐（同 upsert 更新分支）
                if (updateNotifyFields(event.getId(), now, 1) > 0) {
                    event.setLastNotifyTime(now);
                    event.setNotifyCount(1);
                    return AlertUpsertResult.TRIGGERED_AND_NOTIFIED;
                }
            }
            return AlertUpsertResult.TRIGGERED_ONLY;
        }
    }

    /** @return 是否真正完成恢复流转（DB 更新成功）；事件已被人工终态处置时返回 false，不计入恢复统计 */
    private boolean recoverEvent(AlertEvalContext ctx, AlertRule rule, Long instanceId, String instanceName,
                                 String dimensionKey, double currentValue) {
        String dedupKey = buildDedupKey(rule.getRuleCode(), instanceId, dimensionKey);
        AlertEvent active = ctx.activeEvent(dedupKey);
        if (active == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String triggerValueStr = formatValue(currentValue);
        // 场景综合事件：恢复消息不走规则模板（无 operator/threshold 数值句式可渲染）
        String alertMessage = RULE_TYPE_SCENARIO.equals(rule.getRuleType())
                ? "[" + nullSafe(rule.getRuleName()) + "]，各信号已不再满足场景触发逻辑，告警恢复"
                : messageRenderer.render(
                rule,
                AlertMessageRenderer.MessagePhase.RECOVER,
                active.getEventCode(),
                "recovered",
                instanceName,
                active.getDimensionKey(),
                triggerValueStr,
                active.getThresholdValue(),
                active.getTriggerTime(),
                now,
                active.getTriggerCount()
        );
        // status 守卫：事件可能在本轮评估期间已被人工关闭/静默，不能把终态事件改写回 recovered
        int updated = alertEventMapper.update(
                null,
                new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getId, active.getId())
                        .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                        .set(AlertEvent::getStatus, "recovered")
                        .set(AlertEvent::getRecoveryTime, now)
                        .set(AlertEvent::getTriggerValue, triggerValueStr)
                        .set(AlertEvent::getEvalState, "normal")
                        .set(AlertEvent::getEvalMessage, null)
                        .set(AlertEvent::getLastEvalTime, now)
                        .set(AlertEvent::getAlertMessage, alertMessage));
        // 无论更新成败都剔除轮内缓存：成功则事件已恢复，失败则事件已被终态处置，
        // 两种情况下缓存快照都已陈旧，同轮后续逻辑不应再基于它操作
        ctx.activeEvents().remove(dedupKey);
        if (updated == 0) {
            log.info("事件已在评估期间被终态处置，跳过恢复流转 dedupKey={}", dedupKey);
            return false;
        }
        AlertEvent snapshot = buildRecoverNotifySnapshot(active, triggerValueStr, now, alertMessage);
        // 与触发通知对称：恢复通知派发后同步刷新 lastNotifyTime/notifyCount，便于审计与后续去重。
        // 事件刚被本方法置为 recovered，二次更新以 recovered 为守卫，避免覆盖并发处置；
        // 0 行受影响说明事件状态又被改动，仅记日志（通知已派发不可撤回）
        if (alertNotificationService.notifyOnRecovery(rule, snapshot)) {
            int notifyUpdated = alertEventMapper.update(
                    null,
                    new LambdaUpdateWrapper<AlertEvent>()
                            .eq(AlertEvent::getId, active.getId())
                            .eq(AlertEvent::getStatus, "recovered")
                            .set(AlertEvent::getLastNotifyTime, now)
                            .set(AlertEvent::getNotifyCount,
                                    (active.getNotifyCount() == null ? 0 : active.getNotifyCount()) + 1));
            if (notifyUpdated == 0) {
                log.warn("恢复通知字段回写未生效（事件状态已变化）dedupKey={}", dedupKey);
            }
        }
        log.info("告警事件恢复 rule=[{}] instance=[{}]", rule.getRuleCode(), instanceId);
        return true;
    }

    /**
     * 指标缺失兜底建单：仅在开启开关且无活跃事件、且不处于人工静默窗口时建单。
     * 建单沿用规则的 dedupKey，因此数据恢复后可通过正常评估路径（满足恢复条件）或自愈兜底自动恢复。
     */
    private boolean createMetricMissingEvent(AlertEvalContext ctx, AlertRule rule, Long instanceId,
                                             String instanceName, String dimensionKey, String dedupKey) {
        if (isInSilenceWindow(ctx, dedupKey)) {
            return false;
        }
        if (!isRuleStillEnabled(rule, instanceId)) {
            log.info("规则已在评估期间停用，跳过指标缺失兜底建单 rule=[{}] instance=[{}]", rule.getRuleCode(), instanceId);
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String message = "指标 " + nullSafe(rule.getMetricName()) + " 在实例[" + nullSafe(instanceName)
                + "]持续无采集数据，可能采集中断，请及时排查";
        AlertEvent event = new AlertEvent();
        event.setEventCode(newEventCode());
        event.setRuleId(rule.getId());
        event.setRuleName(rule.getRuleName());
        event.setRuleLevel(rule.getRuleLevel());
        event.setInstanceId(instanceId);
        event.setInstanceName(instanceName);
        event.setTriggerValue("-");
        event.setThresholdValue(extractThreshold(rule.getConditionConfig()));
        event.setDimensionKey(dimensionKey);
        event.setDedupKey(dedupKey);
        event.setTriggerCount(1);
        event.setTriggerTime(now);
        event.setLastTriggerTime(now);
        event.setStatus("pending");
        event.setAlertMessage(message);
        event.setNotifyCount(0);
        event.setEvalState("metric_missing");
        event.setEvalMessage(message);
        event.setLastEvalTime(now);
        try {
            alertEventMapper.insert(event);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // 并发/已存在活跃事件：放弃本轮建单
            log.info("指标缺失兜底建单冲突，放弃 dedupKey={}", dedupKey);
            return false;
        }
        ctx.activeEvents().put(dedupKey, event);
        log.info("指标缺失兜底建单 rule=[{}] instance=[{}]", rule.getRuleCode(), instanceId);
        if (shouldSendTriggerNotification(rule, null, now)
                && alertNotificationService.notifyOnTrigger(rule, event)) {
            if (updateNotifyFields(event.getId(), now, 1) > 0) {
                event.setLastNotifyTime(now);
                event.setNotifyCount(1);
            }
        }
        return true;
    }

    // ── 持续窗口 ─────────────────────────────────────────────────────────────

    private OffsetDateTime touchWindow(AlertEvalContext ctx, String dedupKey, String windowType, OffsetDateTime now) {
        OffsetDateTime expireTime = now.plusHours(WINDOW_EXPIRE_HOURS);
        alertEvaluateWindowMapper.touch(dedupKey, windowType, now, expireTime);
        // touch 后统一回查 DB 取 first_match_time，不信任轮内缓存：
        // 窗口可能在本轮评估期间被人工处置（静默/关闭/规则停用）从 DB 删除，touch 已按 now 重建，
        // 若沿用缓存中的旧 first_match_time 会使持续时长判定失真（提前建单/恢复）。
        // 只有 pending 窗口会走到这里，行数很小，每轮一次点查代价可忽略。
        AlertEvaluateWindow window = alertEvaluateWindowMapper.selectWindow(dedupKey, windowType);
        if (window != null) {
            ctx.putWindow(window);
        }
        return window != null && window.getFirstMatchTime() != null ? window.getFirstMatchTime() : now;
    }

    /** 删除持续窗口：快照中不存在则跳过写库（正常态占绝大多数，避免每轮无谓 DELETE）。 */
    private void deleteWindow(AlertEvalContext ctx, String dedupKey, String windowType) {
        if (!ctx.hasWindow(dedupKey, windowType)) {
            return;
        }
        try {
            alertEvaluateWindowMapper.deleteWindow(dedupKey, windowType);
            ctx.removeWindow(dedupKey, windowType);
        } catch (Exception e) {
            log.warn("删除告警持续窗口失败 dedupKey={} type={} err={}",
                    dedupKey, windowType, e.getMessage());
        }
    }

    // ── 通知判定与内部工具 ───────────────────────────────────────────────────

    public boolean isInSilenceWindow(AlertEvalContext ctx, String dedupKey) {
        OffsetDateTime until = ctx.silenceUntil().get(dedupKey);
        return until != null && until.isAfter(OffsetDateTime.now());
    }

    private boolean shouldSendTriggerNotification(AlertRule rule, OffsetDateTime lastNotifyTime, OffsetDateTime now) {
        if (lastNotifyTime == null) {
            return true;
        }
        int silenceMinutes = readSilencePeriodMinutes(rule.getNotificationConfig());
        if (silenceMinutes <= 0) {
            return true;
        }
        OffsetDateTime next = lastNotifyTime.plusMinutes(silenceMinutes);
        return !now.isBefore(next);
    }

    /**
     * 重复通知静默期（分钟）。规则未显式配置时回退到全局默认
     * {@code alert.notify.default-silence-period-minutes}，防止持续异常时每个扫描周期都重发；
     * 显式配置 0 表示每次触发都通知。
     */
    private int readSilencePeriodMinutes(Map<String, Object> notificationConfig) {
        int defaultMinutes = Math.max(0, notifyProperties.getDefaultSilencePeriodMinutes());
        if (notificationConfig == null) {
            return defaultMinutes;
        }
        Object raw = notificationConfig.get("silencePeriod");
        if (raw instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        if (raw instanceof String s) {
            try {
                return Math.max(0, Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                return defaultMinutes;
            }
        }
        return defaultMinutes;
    }

    private int clearEvalState(String dedupKey) {
        return alertEventMapper.update(
                null,
                new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getDedupKey, dedupKey)
                        .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                        .set(AlertEvent::getEvalState, "normal")
                        .set(AlertEvent::getEvalMessage, null)
                        .set(AlertEvent::getLastEvalTime, OffsetDateTime.now())
        );
    }

    private AlertEvent buildNotifySnapshot(AlertEvent existing, String triggerValue, String thresholdValue,
                                           int triggerCount, OffsetDateTime now, String alertMessage) {
        AlertEvent snapshot = new AlertEvent();
        snapshot.setId(existing.getId());
        snapshot.setEventCode(existing.getEventCode());
        snapshot.setRuleId(existing.getRuleId());
        snapshot.setRuleName(existing.getRuleName());
        snapshot.setRuleLevel(existing.getRuleLevel());
        snapshot.setInstanceId(existing.getInstanceId());
        snapshot.setInstanceName(existing.getInstanceName());
        snapshot.setStatus(existing.getStatus());
        snapshot.setThresholdValue(thresholdValue);
        snapshot.setTriggerTime(existing.getTriggerTime());
        snapshot.setTriggerValue(triggerValue);
        snapshot.setTriggerCount(triggerCount);
        snapshot.setLastTriggerTime(now);
        snapshot.setAlertMessage(alertMessage);
        return snapshot;
    }

    private AlertEvent buildRecoverNotifySnapshot(AlertEvent existing, String triggerValue, OffsetDateTime now, String alertMessage) {
        AlertEvent snapshot = new AlertEvent();
        snapshot.setId(existing.getId());
        snapshot.setEventCode(existing.getEventCode());
        snapshot.setRuleId(existing.getRuleId());
        snapshot.setRuleName(existing.getRuleName());
        snapshot.setRuleLevel(existing.getRuleLevel());
        snapshot.setInstanceId(existing.getInstanceId());
        snapshot.setInstanceName(existing.getInstanceName());
        snapshot.setStatus("recovered");
        snapshot.setThresholdValue(existing.getThresholdValue());
        snapshot.setTriggerTime(existing.getTriggerTime());
        snapshot.setTriggerValue(triggerValue);
        snapshot.setTriggerCount(existing.getTriggerCount());
        snapshot.setLastTriggerTime(now);
        snapshot.setRecoveryTime(now);
        snapshot.setAlertMessage(alertMessage);
        return snapshot;
    }

    /**
     * 建单前回查规则启用状态。builtin 与 custom 的启停统一由
     * {@code alert_rule_instance_config.enabled}（按 ruleCode + instanceId）承载。
     * 查询失败时放行（宁可多建一单，不因监控库抖动漏告警）。
     */
    private boolean isRuleStillEnabled(AlertRule rule, Long instanceId) {
        try {
            // 场景伪规则：启停由 scenario_instance_config 承载
            if (RULE_TYPE_SCENARIO.equals(rule.getRuleType())) {
                Long scenarioCount = scenarioConfigMapper.selectCount(new LambdaQueryWrapper<ScenarioInstanceConfig>()
                        .eq(ScenarioInstanceConfig::getScenarioCode, rule.getRuleCode())
                        .eq(ScenarioInstanceConfig::getInstanceId, instanceId)
                        .eq(ScenarioInstanceConfig::getEnabled, true));
                return scenarioCount != null && scenarioCount > 0;
            }
            Long count = instanceConfigMapper.selectCount(new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                    .eq(AlertRuleInstanceConfig::getRuleCode, rule.getRuleCode())
                    .eq(AlertRuleInstanceConfig::getInstanceId, instanceId)
                    // rule_code 当前全局唯一，rule_type 过滤属防御性约束（与管理侧查询口径对称）
                    .eq(rule.getRuleType() != null, AlertRuleInstanceConfig::getRuleType, rule.getRuleType())
                    .eq(AlertRuleInstanceConfig::getEnabled, true));
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("回查规则启用状态失败，按启用处理 rule=[{}] instance=[{}] err={}",
                    rule.getRuleCode(), instanceId, e.getMessage());
            return true;
        }
    }

    /** 通知字段二次更新：带活跃 status 守卫，事件已被终态处置时静默跳过（通知已派发不可撤回）。 */
    private int updateNotifyFields(Long eventId, OffsetDateTime notifyTime, int notifyCount) {
        int updated = alertEventMapper.update(
                null,
                new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getId, eventId)
                        .in(AlertEvent::getStatus, "pending", "confirmed", "handling")
                        .set(AlertEvent::getLastNotifyTime, notifyTime)
                        .set(AlertEvent::getNotifyCount, notifyCount));
        if (updated == 0) {
            log.warn("通知字段回写未生效（事件已被终态处置，通知已派发不可撤回）eventId={}", eventId);
        }
        return updated;
    }

    /** metric_missing / sql_error 等"评估异常"态（区别于 normal 与自愈写入的 auto_recovered）。 */
    private static boolean isAbnormalEvalState(String evalState) {
        return "metric_missing".equals(evalState) || "sql_error".equals(evalState);
    }

    /** 事件编码生成（包内共享：规则评估建单与系统内置连接失败告警使用同一编码格式）。 */
    static String newEventCode() {
        return "AE" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /** 信号快照序列化为 JSON 字符串（jsonb 列经 stringtype=unspecified 直接落库）。 */
    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return cn.hutool.json.JSONUtil.toJsonStr(value);
        } catch (Exception e) {
            log.warn("信号快照序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private static String buildDedupKey(String ruleCode, Long instanceId, String dimensionKey) {
        return AlertConditionEvaluator.buildDedupKey(ruleCode, instanceId, dimensionKey);
    }

    private static String extractThreshold(Map<String, Object> conditionConfig) {
        if (conditionConfig == null) {
            return "";
        }
        Object op = conditionConfig.get("operator");
        Object th = conditionConfig.get("threshold");
        return (op != null ? op : "") + (th != null ? th.toString() : "");
    }

    private static String formatValue(double value) {
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
