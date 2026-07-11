package com.lzzh.monitor.service.scenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景条件组树求值（纯函数，可脱离 Spring 单测）。
 *
 * <p>条件组树 schema（可嵌套，UI 首期单层）：
 * <pre>{@code
 * {"logic":"AND|OR","duration":秒,
 *  "children":[
 *    {"type":"condition","code":"...","name":"...","metricCode":"...",
 *     "condType":"threshold|rate_change","operator":">=","threshold":85,
 *     "unit":"%","compareOffset":"1h","exprText":"≥ 85%"},
 *    {"type":"group","logic":"OR","children":[...]}
 *  ]}
 * }</pre>
 *
 * <p>三值逻辑：condition 数据缺失时结果为 {@link TriState#UNKNOWN}；
 * AND 组内无 FALSE 且含 UNKNOWN → 组为 UNKNOWN（无法确认成立，不触发也不恢复）；
 * OR 组内任一 TRUE 即 TRUE，否则含 UNKNOWN → UNKNOWN（缺失信号按未满足参与，但整组无法确认为 FALSE）。
 */
public final class ScenarioConditionEvaluator {

    private ScenarioConditionEvaluator() {
    }

    /** 三值求值结果。 */
    public enum TriState { TRUE, FALSE, UNKNOWN }

    /**
     * 单信号求值明细（供列表实时展示与事件信号快照）。
     *
     * @param code         信号编码（条件组树内唯一，用于分支结论匹配）
     * @param name         信号名称
     * @param exprText     触发条件展示文案
     * @param metricCode   指标编码
     * @param unit         数值单位（展示用）
     * @param currentValue 参与比较的值（threshold=指标当前值；rate_change=变化百分比）；数据缺失为 null
     * @param state        求值结果
     */
    public record SignalStatus(String code, String name, String exprText, String metricCode,
                               String unit, Double currentValue, TriState state) {
    }

    /** 组树整体求值结果 + 各叶子信号明细（按树遍历顺序）。 */
    public record EvalOutcome(TriState state, List<SignalStatus> signals) {
    }

    /**
     * 求值条件组树。
     *
     * @param conditionConfig 条件组树根节点
     * @param currentValues   metricCode → 最新值（缺失表示无新鲜数据）
     * @param baselineValues  环比基准值：{@link #baselineKey} → offset 前取值
     */
    public static EvalOutcome evaluate(Map<String, Object> conditionConfig,
                                       Map<String, Double> currentValues,
                                       Map<String, Double> baselineValues) {
        List<SignalStatus> signals = new ArrayList<>();
        TriState state = evaluateNode(conditionConfig, currentValues, baselineValues, signals);
        return new EvalOutcome(state, signals);
    }

    /** 收集组树内全部叶子条件（数据预取用）。 */
    public static List<Map<String, Object>> collectConditions(Map<String, Object> conditionConfig) {
        List<Map<String, Object>> result = new ArrayList<>();
        collectConditions(conditionConfig, result);
        return result;
    }

    /**
     * 应用实例级阈值覆盖：按信号 code 替换叶子条件的 threshold 数值（深拷贝，不改模板对象）。
     * 仅覆盖阈值数字，运算符与组树结构不可改；exprText 中的原阈值数字同步替换以保持展示一致。
     *
     * @param conditionConfig 场景模板条件组树
     * @param overrides       信号 code → 新阈值；null/空表示无覆盖（原样返回模板树）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> applyOverrides(Map<String, Object> conditionConfig,
                                                     Map<String, Object> overrides) {
        if (conditionConfig == null || overrides == null || overrides.isEmpty()) {
            return conditionConfig;
        }
        Map<String, Object> copy = new HashMap<>(conditionConfig);
        if ("condition".equals(copy.get("type"))) {
            String code = asString(copy.get("code"));
            Double newThreshold = code == null ? null : toDouble(overrides.get(code));
            if (newThreshold != null) {
                Double oldThreshold = toDouble(copy.get("threshold"));
                copy.put("threshold", newThreshold);
                String exprText = asString(copy.get("exprText"));
                if (exprText != null && oldThreshold != null) {
                    copy.put("exprText", exprText.replace(formatNumber(oldThreshold), formatNumber(newThreshold)));
                }
            }
            return copy;
        }
        if (copy.get("children") instanceof List<?> children) {
            List<Object> newChildren = new ArrayList<>(children.size());
            for (Object child : children) {
                newChildren.add(child instanceof Map
                        ? applyOverrides((Map<String, Object>) child, overrides)
                        : child);
            }
            copy.put("children", newChildren);
        }
        return copy;
    }

    /** 整数阈值不带小数点展示（85 而非 85.0），与种子 SQL 中 exprText 的写法一致。 */
    public static String formatNumber(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    /** 环比基准值 map 的键：同一指标可能配置不同 offset。 */
    public static String baselineKey(String metricCode, String compareOffset) {
        return metricCode + "@" + (compareOffset == null ? "" : compareOffset);
    }

    /** 解析 compareOffset（"30m"/"1h"/"1d"）为分钟数；非法返回 60（默认 1 小时）。 */
    public static int offsetMinutes(String compareOffset) {
        if (compareOffset == null || compareOffset.isBlank()) {
            return 60;
        }
        String s = compareOffset.trim().toLowerCase();
        try {
            int num = Integer.parseInt(s.substring(0, s.length() - 1));
            return switch (s.charAt(s.length() - 1)) {
                case 'm' -> Math.max(1, num);
                case 'h' -> Math.max(1, num) * 60;
                case 'd' -> Math.max(1, num) * 24 * 60;
                default -> 60;
            };
        } catch (Exception ignored) {
            return 60;
        }
    }

    /**
     * 按命中信号组合匹配分支诊断结论：branch.when 内信号全部命中时匹配（首个匹配生效）；
     * 无匹配返回 {@code defaultText}。
     */
    public static String resolveDiagnosis(List<Map<String, Object>> branches,
                                          List<SignalStatus> signals,
                                          String defaultText) {
        if (branches == null || branches.isEmpty()) {
            return defaultText;
        }
        List<String> metCodes = signals.stream()
                .filter(s -> s.state() == TriState.TRUE)
                .map(SignalStatus::code)
                .toList();
        for (Map<String, Object> branch : branches) {
            Object when = branch.get("when");
            if (!(when instanceof List<?> codes) || codes.isEmpty()) {
                continue;
            }
            boolean allMet = codes.stream().allMatch(c -> c instanceof String s && metCodes.contains(s));
            if (allMet && branch.get("text") instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return defaultText;
    }

    // ── 内部递归求值 ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static TriState evaluateNode(Map<String, Object> node,
                                         Map<String, Double> currentValues,
                                         Map<String, Double> baselineValues,
                                         List<SignalStatus> signals) {
        if (node == null) {
            return TriState.UNKNOWN;
        }
        // condition 叶子节点
        if ("condition".equals(node.get("type"))) {
            return evaluateCondition(node, currentValues, baselineValues, signals);
        }
        // group 节点（根节点无 type 字段，也按 group 处理）
        Object childrenObj = node.get("children");
        if (!(childrenObj instanceof List<?> children) || children.isEmpty()) {
            return TriState.UNKNOWN;
        }
        boolean isOr = "OR".equalsIgnoreCase(String.valueOf(node.get("logic")));
        boolean anyUnknown = false;
        boolean anyTrue = false;
        boolean anyFalse = false;
        for (Object childObj : children) {
            if (!(childObj instanceof Map)) {
                continue;
            }
            TriState child = evaluateNode((Map<String, Object>) childObj, currentValues, baselineValues, signals);
            switch (child) {
                case TRUE -> anyTrue = true;
                case FALSE -> anyFalse = true;
                case UNKNOWN -> anyUnknown = true;
            }
        }
        if (isOr) {
            // OR：任一 TRUE 即触发；缺失信号按未满足处理，但全组无 TRUE 且有缺失时无法确认 FALSE
            if (anyTrue) {
                return TriState.TRUE;
            }
            return anyUnknown ? TriState.UNKNOWN : TriState.FALSE;
        }
        // AND：任一 FALSE 即不成立；无 FALSE 但有缺失时无法确认 TRUE
        if (anyFalse) {
            return TriState.FALSE;
        }
        return anyUnknown ? TriState.UNKNOWN : (anyTrue ? TriState.TRUE : TriState.UNKNOWN);
    }

    private static TriState evaluateCondition(Map<String, Object> cond,
                                              Map<String, Double> currentValues,
                                              Map<String, Double> baselineValues,
                                              List<SignalStatus> signals) {
        String code = asString(cond.get("code"));
        String name = asString(cond.get("name"));
        String exprText = asString(cond.get("exprText"));
        String metricCode = asString(cond.get("metricCode"));
        String unit = asString(cond.get("unit"));
        String operator = asString(cond.get("operator"));
        Double threshold = toDouble(cond.get("threshold"));

        Double compareValue = resolveCompareValue(cond, metricCode, currentValues, baselineValues);
        TriState state;
        if (compareValue == null || operator == null || threshold == null) {
            state = TriState.UNKNOWN;
        } else {
            state = compare(compareValue, operator, threshold) ? TriState.TRUE : TriState.FALSE;
        }
        signals.add(new SignalStatus(code, name, exprText, metricCode, unit, compareValue, state));
        return state;
    }

    /** threshold=指标当前值；rate_change=(当前-基准)/基准×100（基准缺失或为 0 时无法计算）。 */
    private static Double resolveCompareValue(Map<String, Object> cond, String metricCode,
                                              Map<String, Double> currentValues,
                                              Map<String, Double> baselineValues) {
        if (metricCode == null) {
            return null;
        }
        Double current = currentValues.get(metricCode);
        if (current == null) {
            return null;
        }
        if (!"rate_change".equals(cond.get("condType"))) {
            return current;
        }
        Double base = baselineValues.get(baselineKey(metricCode, asString(cond.get("compareOffset"))));
        if (base == null || base == 0.0) {
            return null;
        }
        return (current - base) / base * 100.0;
    }

    /** 数值比较（与告警规则引擎语义一致）：支持 >, >=, <, <=, =/==, !=；未知运算符返回 false。 */
    static boolean compare(double value, String operator, double threshold) {
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

    private static String asString(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static Double toDouble(Object raw) {
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

    private static void collectConditions(Map<String, Object> node, List<Map<String, Object>> result) {
        if (node == null) {
            return;
        }
        if ("condition".equals(node.get("type"))) {
            result.add(node);
            return;
        }
        if (node.get("children") instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> childMap = (Map<String, Object>) child;
                    collectConditions(childMap, result);
                }
            }
        }
    }
}
