package com.lzzh.monitor.collector.alert;

import com.lzzh.monitor.dao.entity.AlertRule;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 告警消息文案渲染：触发/恢复文案、自定义 multi 规则的占位符模板渲染。
 * 从 AlertEvaluateJobHandler 拆出，纯函数式组件，无状态。
 */
@Component
public class AlertMessageRenderer {

    /** 消息阶段：触发 / 恢复。 */
    public enum MessagePhase {
        TRIGGER,
        RECOVER
    }

    private static final String DEFAULT_MULTI_TEMPLATE = "监控对象{dimensionKey}当前值{triggerValue}，超过阈值{thresholdValue}";
    private static final Pattern TEMPLATE_PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    public String render(AlertRule rule,
                         MessagePhase phase,
                         String eventCode,
                         String status,
                         String instanceName,
                         String dimensionKey,
                         String triggerValue,
                         String thresholdValue,
                         OffsetDateTime triggerTime,
                         OffsetDateTime lastTriggerTime,
                         Integer triggerCount) {
        if (isCustomMulti(rule)) {
            String multiTemplate = extractDisplayTemplate(rule.getConditionConfig());
            String rendered = renderByTemplate(multiTemplate, rule, eventCode, status, instanceName, dimensionKey, triggerValue,
                    thresholdValue, triggerTime, lastTriggerTime, triggerCount);
            return rendered + (phase == MessagePhase.RECOVER ? "，告警恢复" : "，告警触发");
        }

        String phaseText = phase == MessagePhase.RECOVER ? "告警恢复" : "告警触发";

        // 布尔型规则（复制线程停止等）："当前值 0，小于1"这类数值句式没有业务含义，
        // 改用条件配置里的 displayText 友好描述拼接消息
        String booleanText = extractBooleanStateText(rule, phase);
        if (booleanText != null) {
            return "[" + nullSafe(rule.getRuleName()) + "]，" + booleanText + "，" + phaseText;
        }

        String conditionText = phase == MessagePhase.RECOVER ? extractRecoveryCondition(rule) : extractTriggerCondition(rule);
        return "[" + nullSafe(rule.getRuleName()) + "]，当前值 " + withUnit(triggerValue, rule) + "，" + conditionText + "，" + phaseText;
    }

    /** 当前值拼接指标单位（如 21.19%），非数值（'-'/空）不拼接。 */
    private String withUnit(String triggerValue, AlertRule rule) {
        String value = nullSafe(triggerValue);
        if (value.isBlank() || "-".equals(value)) {
            return value;
        }
        Map<String, Object> cond = rule.getConditionConfig();
        String unit = cond == null || cond.get("unit") == null ? "" : cond.get("unit").toString();
        return value + unitLabel(unit);
    }

    /**
     * 单位展示归一：历史配置可能存的是指标定义的单位编码（percent/count 等），
     * 拼进文案前转为展示符号；计数类不带单位。
     */
    private static String unitLabel(String unit) {
        if (unit == null) return "";
        return switch (unit.trim()) {
            case "percent" -> "%";
            case "count", "qps" -> "";
            case "seconds" -> "秒";
            case "bytes" -> "字节";
            default -> unit.trim();
        };
    }

    /**
     * 布尔型规则（conditionConfig.conditionType = boolean）的状态描述：
     * 取触发/恢复配置里的 displayText，并剥离面向条件展示的"时立即触发/时自动恢复"尾缀，
     * 例如「检测到复制 IO 线程停止（Slave_IO_Running = No）」。非布尔型或缺文案时返回 null 走数值句式。
     */
    private String extractBooleanStateText(AlertRule rule, MessagePhase phase) {
        Map<String, Object> cond = rule.getConditionConfig();
        if (cond == null || !"boolean".equals(cond.get("conditionType"))) {
            return null;
        }
        Map<String, Object> source = phase == MessagePhase.RECOVER ? rule.getRecoveryConfig() : cond;
        Object raw = source == null ? null : source.get("displayText");
        if (!(raw instanceof String text) || text.isBlank()) {
            return null;
        }
        text = text.trim();
        for (String suffix : new String[]{"时立即触发", "时自动恢复", "时触发", "时恢复"}) {
            if (text.endsWith(suffix)) {
                return text.substring(0, text.length() - suffix.length());
            }
        }
        return text;
    }

    private String renderByTemplate(String template, AlertRule rule,
                                    String eventCode, String status, String instanceName, String dimensionKey,
                                    String triggerValue, String thresholdValue,
                                    OffsetDateTime triggerTime, OffsetDateTime lastTriggerTime,
                                    Integer triggerCount) {
        if (template == null || template.isBlank()) {
            template = DEFAULT_MULTI_TEMPLATE;
        }
        Map<String, String> ctx = new HashMap<>();
        ctx.put("ruleName", nullSafe(rule.getRuleName()));
        ctx.put("ruleLevel", nullSafe(rule.getRuleLevel()));
        ctx.put("metricName", nullSafe(rule.getMetricName()));
        ctx.put("instanceName", nullSafe(instanceName));
        ctx.put("dimensionKey", nullSafe(dimensionKey));
        ctx.put("triggerValue", nullSafe(triggerValue));
        ctx.put("thresholdValue", nullSafe(thresholdValue));
        ctx.put("eventCode", nullSafe(eventCode));
        ctx.put("status", nullSafe(status));
        ctx.put("triggerCount", triggerCount == null ? "" : String.valueOf(triggerCount));
        ctx.put("triggerTime", triggerTime == null ? "" : triggerTime.toString());
        ctx.put("lastTriggerTime", lastTriggerTime == null ? "" : lastTriggerTime.toString());

        Matcher matcher = TEMPLATE_PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = ctx.containsKey(key) ? ctx.get(key) : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString().trim();
    }

    private String extractDisplayTemplate(Map<String, Object> conditionConfig) {
        if (conditionConfig == null) {
            return DEFAULT_MULTI_TEMPLATE;
        }
        Object raw = conditionConfig.get("displayTemplate");
        if (raw instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return DEFAULT_MULTI_TEMPLATE;
    }

    private boolean isCustomMulti(AlertRule rule) {
        if (!"custom".equals(rule.getRuleType()) || rule.getConditionConfig() == null) {
            return false;
        }
        Object mode = rule.getConditionConfig().get("resultMode");
        return mode instanceof String s && "multi".equalsIgnoreCase(s);
    }

    private String extractTriggerCondition(AlertRule rule) {
        return extractConditionText(rule.getConditionConfig(), "");
    }

    private String extractRecoveryCondition(AlertRule rule) {
        Map<String, Object> recovery = rule.getRecoveryConfig();
        if (recovery != null && !recovery.isEmpty()) {
            return extractConditionText(recovery, "");
        }
        Map<String, Object> cond = rule.getConditionConfig();
        if (cond == null) {
            return "";
        }
        String op = cond.get("operator") == null ? "" : cond.get("operator").toString();
        String inverse = AlertConditionEvaluator.inverseOperator(op);
        Object threshold = cond.get("threshold");
        String unit = cond.get("unit") == null ? "" : cond.get("unit").toString();
        return inverse + (threshold == null ? "" : threshold.toString()) + unitLabel(unit);
    }

    private String extractConditionText(Map<String, Object> cfg, String fallback) {
        if (cfg == null) return fallback;
        String op = cfg.get("operator") == null ? "" : cfg.get("operator").toString();
        Object threshold = cfg.get("threshold");
        String unit = cfg.get("unit") == null ? "" : cfg.get("unit").toString();
        if (op.isBlank() && threshold == null) return fallback;
        return operatorLabel(op) + (threshold == null ? "" : threshold.toString()) + unitLabel(unit);
    }

    private String operatorLabel(String op) {
        return switch (op) {
            case ">" -> "大于";
            case ">=" -> "大于等于";
            case "<" -> "小于";
            case "<=" -> "小于等于";
            case "=", "==" -> "等于";
            case "!=" -> "不等于";
            default -> op;
        };
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
