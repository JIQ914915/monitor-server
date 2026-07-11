package com.lzzh.monitor.collector.alert;

import java.util.Map;

/**
 * 告警条件评估的纯函数集合，从 {@code AlertEvaluateJobHandler} 抽取而来。
 *
 * <p>之所以独立成类：{@code AlertEvaluateJobHandler} 依赖 XXL-Job/MyBatis-Plus/JDBC 等大量运行时环境，
 * 难以直接做单元测试；而条件比较、恢复判定、去重键构造这些逻辑本身是无副作用的纯函数，
 * 抽出后可以脱离 Spring 容器直接测试，覆盖告警评估中风险最高的部分（阈值判断、持续窗口）。
 */
public final class AlertConditionEvaluator {

    private AlertConditionEvaluator() {
    }

    /** 按运算符比较数值：支持 {@code >, >=, <, <=, =/==, !=}；未知运算符返回 false。 */
    public static boolean compare(double value, String operator, double threshold) {
        if (operator == null) {
            return false;
        }
        return switch (operator) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "=", "==" -> Double.compare(value, threshold) == 0;
            case "!=" -> Double.compare(value, threshold) != 0;
            default -> false;
        };
    }

    /** 运算符取反，用于"未显式配置恢复条件时以触发条件反义作恢复"。未知运算符返回 {@code ==}（永不满足数值比较，保守不误恢复）。 */
    public static String inverseOperator(String op) {
        if (op == null) {
            return "==";
        }
        return switch (op) {
            case ">" -> "<=";
            case ">=" -> "<";
            case "<" -> ">=";
            case "<=" -> ">";
            default -> "==";
        };
    }

    /** 按 {@code conditionConfig = {"operator":..,"threshold":..}} 判断是否触发。缺失 operator/threshold 视为不触发。 */
    public static boolean checkCondition(Map<String, Object> conditionConfig, double value) {
        if (conditionConfig == null) {
            return false;
        }
        Object operatorObj = conditionConfig.get("operator");
        Double threshold = toDouble(conditionConfig.get("threshold"));
        if (!(operatorObj instanceof String operator) || threshold == null) {
            return false;
        }
        return compare(value, operator, threshold);
    }

    /**
     * 宽松解析阈值：兼容 JSON 中 threshold 为数值（{@code 90}）或字符串（{@code "90"}）两种形态。
     * 前端表单/历史数据可能把阈值序列化为字符串，若只接受 {@link Number} 会导致规则"静默不触发"。
     * 无法解析（null / 非数字字符串）时返回 {@code null}，由调用方按"缺失阈值"处理。
     */
    static Double toDouble(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 判断是否满足恢复条件：{@code recoveryConfig} 非空时按其判断；否则以 {@code conditionConfig} 运算符反义判断。
     */
    public static boolean checkRecovery(Map<String, Object> conditionConfig, Map<String, Object> recoveryConfig, double value) {
        if (recoveryConfig != null && !recoveryConfig.isEmpty()) {
            return checkCondition(recoveryConfig, value);
        }
        if (conditionConfig == null) {
            return false;
        }
        Object operatorObj = conditionConfig.get("operator");
        Double threshold = toDouble(conditionConfig.get("threshold"));
        if (!(operatorObj instanceof String operator) || threshold == null) {
            return false;
        }
        return compare(value, inverseOperator(operator), threshold);
    }

    /**
     * 从条件配置中读取持续时间（秒），非法/缺失返回 0；负数按 0 处理。
     *
     * <p><b>语义澄清</b>：{@code duration} 是"墙钟秒数"而非"连续评估次数"。触发/恢复的持续判定基于
     * 持续窗口的 {@code first_match_time} 到当前时间的墙钟间隔，与规则的 {@code scanIntervalMin}（扫描间隔）
     * 叠加：由于评估按扫描间隔离散执行，实际生效的等待时间会被向上取整到扫描间隔的整数倍。
     * 例如 {@code duration=90s} 且 {@code scanIntervalMin=1} 时，实际约需连续 2 个评估周期（≥90s）才建单；
     * {@code duration<=0} 表示立即触发/恢复，无等待。保存时 {@code validateDurationAgainstInterval}
     * 已强制 {@code duration>=scanIntervalMin*60} 或为 0，避免退化配置。
     */
    public static int readDurationSeconds(Map<String, Object> cfg, String key) {
        if (cfg == null) {
            return 0;
        }
        Object raw = cfg.get(key);
        if (raw instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        if (raw instanceof String s) {
            try {
                return Math.max(0, Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** 归并去重键：{@code ruleCode:instanceId[:dimensionKey]}，无对象维度时不含第三段。 */
    public static String buildDedupKey(String ruleCode, Long instanceId, String dimensionKey) {
        String base = (ruleCode == null ? "" : ruleCode) + ":" + instanceId;
        return (dimensionKey != null && !dimensionKey.isBlank()) ? base + ":" + dimensionKey : base;
    }
}
