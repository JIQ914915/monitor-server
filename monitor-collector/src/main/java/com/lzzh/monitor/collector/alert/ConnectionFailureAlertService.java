package com.lzzh.monitor.collector.alert;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;

/**
 * 实例连接失败告警（系统内置逻辑，不走告警规则体系）。
 *
 * <p>触发与恢复完全跟随实例状态联动（{@code CollectRunner.handleAvailability}）：
 * <ul>
 *   <li><b>触发</b>：实例连续 {@value #FAILURE_THRESHOLD} 次连接失败、状态被标为 {@code abnormal} 的瞬间，
 *       建一条一级告警事件并全渠道通知（邮件/短信/Webhook/钉钉/企业微信/飞书）；</li>
 *   <li><b>恢复</b>：连接恢复、状态还原为 {@code normal} 的瞬间，事件流转为 {@code recovered}
 *       并发送恢复通知。</li>
 * </ul>
 *
 * <p><b>复用既有封装，不另造轮子</b>：事件编码复用 {@link AlertEventLifecycleService#newEventCode()}，
 * 通知派发复用 {@link AlertNotificationService}（通道解析、异步发送、失败重试、通知记录落库、
 * 风暴抑制全部沿用）。文案为布尔型故障专属固定文案（"已重试 N 次仍然连接失败"/"连接恢复"），
 * 不套用通用渲染器的"当前值/阈值"句式。本类只负责"何时触发/恢复"的编排与事件落库。
 *
 * <p>无对应 {@code alert_rule} 行（rule_id 为 NULL），规则名称/级别以事件快照冗余承载；
 * 用户不可编辑、不可停用，也不在规则管理页面出现。活跃事件靠 {@code uk_alert_event_active_dedup}
 * 部分唯一索引归并去重，同一实例持续宕机不会重复建单。
 */
@Service
public class ConnectionFailureAlertService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionFailureAlertService.class);

    /** 事件/通知记录中承载的规则编码（仅作标识，alert_rule 中无此行）。 */
    public static final String RULE_CODE = com.lzzh.monitor.common.constant.Constants.SYSTEM_RULE_CONNECTION_FAILURE;
    /** 连续失败触发阈值（与 CollectRunner 标 abnormal 的阈值同源）。 */
    public static final int FAILURE_THRESHOLD = 3;

    private static final String RULE_NAME = "实例连接失败";
    private static final String RULE_LEVEL = "level_1";
    private static final List<String> ACTIVE_STATUSES = List.of("pending", "confirmed", "handling");

    private final AlertEventMapper alertEventMapper;
    private final AlertNotificationService notificationService;

    public ConnectionFailureAlertService(AlertEventMapper alertEventMapper,
                                         AlertNotificationService notificationService) {
        this.alertEventMapper = alertEventMapper;
        this.notificationService = notificationService;
    }

    /**
     * 实例被标为 abnormal（连续失败达阈值）时建单并全渠道通知。
     * <p>持续宕机期间每轮都会进入：先查活跃事件去重，唯一索引兜底并发竞争。
     * 任何异常只记日志，不影响采集主流程。
     */
    public void onInstanceDown(Long instanceId, String instanceName, int consecutiveFails, String lastError) {
        try {
            String dedupKey = RULE_CODE + ":" + instanceId;
            boolean exists = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                    .eq(AlertEvent::getDedupKey, dedupKey)
                    .in(AlertEvent::getStatus, ACTIVE_STATUSES)) > 0;
            if (exists) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now();
            AlertRule rule = syntheticRule();
            String eventCode = AlertEventLifecycleService.newEventCode();
            String triggerValue = String.valueOf(consecutiveFails);
            String thresholdValue = String.valueOf(FAILURE_THRESHOLD);
            // 连接失败是布尔型故障，不适用通用渲染器的"当前值/阈值"句式，使用专属固定文案
            String message = "[" + RULE_NAME + "]，已重试" + consecutiveFails + "次，仍然连接失败，告警触发";
            if (lastError != null && !lastError.isBlank()) {
                message = message + "。最近错误：" + truncate(lastError, 300);
            }

            AlertEvent event = new AlertEvent();
            event.setEventCode(eventCode);
            event.setRuleId(null);
            event.setEventSource("system");
            event.setRuleName(RULE_NAME);
            event.setRuleLevel(RULE_LEVEL);
            event.setInstanceId(instanceId);
            event.setInstanceName(instanceName);
            event.setTriggerValue(triggerValue);
            event.setThresholdValue(thresholdValue);
            event.setAlertMessage(message);
            event.setDedupKey(dedupKey);
            event.setTriggerCount(1);
            event.setTriggerTime(now);
            event.setLastTriggerTime(now);
            event.setStatus("pending");
            event.setNotifyCount(0);
            event.setEvalState("normal");
            event.setLastEvalTime(now);
            try {
                alertEventMapper.insert(event);
            } catch (DataIntegrityViolationException dup) {
                // 与并发建单撞唯一索引：已有活跃事件，不重复建单、不重复通知
                log.debug("实例 {} 连接失败告警建单冲突（已有活跃事件），跳过", instanceId);
                return;
            }
            log.warn("实例 {} 连接失败告警建单 event={}", instanceId, event.getEventCode());

            if (notificationService.notifyOnTrigger(rule, event)) {
                alertEventMapper.update(null, new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getId, event.getId())
                        .set(AlertEvent::getLastNotifyTime, now)
                        .set(AlertEvent::getNotifyCount, 1));
            }
        } catch (Exception e) {
            log.error("实例 {} 连接失败告警处理异常", instanceId, e);
        }
    }

    /**
     * 实例连接恢复（abnormal → normal）时事件流转为 recovered 并发送恢复通知。
     * <p>带状态守卫：事件已被人工关闭/忽略时不改写终态、不发恢复通知。
     */
    public void onInstanceRecovered(Long instanceId, String instanceName) {
        try {
            String dedupKey = RULE_CODE + ":" + instanceId;
            AlertEvent active = alertEventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                    .eq(AlertEvent::getDedupKey, dedupKey)
                    .in(AlertEvent::getStatus, ACTIVE_STATUSES)
                    .last("LIMIT 1"));
            if (active == null) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now();
            AlertRule rule = syntheticRule();
            String triggerValue = "0";
            // 与触发文案对称的专属固定文案
            String message = "[" + RULE_NAME + "]，连接正常，告警恢复";
            int updated = alertEventMapper.update(null, new LambdaUpdateWrapper<AlertEvent>()
                    .eq(AlertEvent::getId, active.getId())
                    .in(AlertEvent::getStatus, ACTIVE_STATUSES)
                    .set(AlertEvent::getStatus, "recovered")
                    .set(AlertEvent::getRecoveryTime, now)
                    .set(AlertEvent::getTriggerValue, triggerValue)
                    .set(AlertEvent::getAlertMessage, message));
            if (updated == 0) {
                // 查询到更新之间事件被人工终态处置，不发恢复通知
                return;
            }
            log.info("实例 {} 连接失败告警已恢复 event={}", instanceId, active.getEventCode());

            active.setStatus("recovered");
            active.setRecoveryTime(now);
            active.setTriggerValue(triggerValue);
            active.setAlertMessage(message);
            if (notificationService.notifyOnRecovery(rule, active)) {
                alertEventMapper.update(null, new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getId, active.getId())
                        .eq(AlertEvent::getStatus, "recovered")
                        .set(AlertEvent::getLastNotifyTime, now)
                        .set(AlertEvent::getNotifyCount,
                                (active.getNotifyCount() == null ? 0 : active.getNotifyCount()) + 1));
            }
        } catch (Exception e) {
            log.error("实例 {} 连接恢复通知处理异常", instanceId, e);
        }
    }

    /**
     * 渲染与通知派发用的内存规则对象：一级告警 + 触发/恢复均通知 + 全部通知渠道，
     * 条件配置仅供 {@link AlertMessageRenderer} 生成"大于等于3次/小于3次"的条件文案。
     * 具体通道能否送达仍取决于全局通道配置与实例联系人，未配置的通道由通知服务跳过。
     */
    private AlertRule syntheticRule() {
        AlertRule rule = new AlertRule();
        rule.setRuleCode(RULE_CODE);
        rule.setRuleName(RULE_NAME);
        rule.setRuleType("builtin");
        rule.setRuleLevel(RULE_LEVEL);
        rule.setConditionConfig(Map.of(
                "operator", ">=",
                "threshold", FAILURE_THRESHOLD,
                "unit", "次"
        ));
        rule.setNotificationConfig(Map.of(
                "notifyOnTrigger", true,
                "notifyOnRecovery", true,
                "channelEmail", true,
                "channelSms", true,
                "channelWebhook", true,
                "channelDingtalk", true,
                "channelWecom", true,
                "channelFeishu", true
        ));
        return rule;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
