package com.lzzh.monitor.service.scenario;

import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator.EvalOutcome;
import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator.SignalStatus;
import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator.TriState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScenarioConditionEvaluator} 单元测试：覆盖嵌套组树求值（AND/OR/混合）、
 * 三值逻辑（unknown 传播）、环比条件、诊断分支匹配与边界（空组/未知运算符）。
 */
class ScenarioConditionEvaluatorTest {

    // ── 构造工具 ────────────────────────────────────────────────────────────

    private static Map<String, Object> threshold(String code, String metric, String op, double value) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "condition");
        m.put("code", code);
        m.put("name", code);
        m.put("metricCode", metric);
        m.put("condType", "threshold");
        m.put("operator", op);
        m.put("threshold", value);
        return m;
    }

    private static Map<String, Object> rateChange(String code, String metric, String offset, String op, double value) {
        Map<String, Object> m = threshold(code, metric, op, value);
        m.put("condType", "rate_change");
        m.put("compareOffset", offset);
        return m;
    }

    private static Map<String, Object> group(String logic, List<Map<String, Object>> children) {
        Map<String, Object> m = new HashMap<>();
        m.put("logic", logic);
        m.put("children", children);
        return m;
    }

    private static Map<String, Object> nestedGroup(String logic, List<Map<String, Object>> children) {
        Map<String, Object> m = group(logic, children);
        m.put("type", "group");
        return m;
    }

    // ── AND / OR 基本组合 ───────────────────────────────────────────────────

    @Nested
    class AndOrGroups {

        @Test
        void andAllTrue() {
            Map<String, Object> root = group("AND", List.of(
                    threshold("a", "m.a", ">=", 85),
                    threshold("b", "m.b", ">", 10)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.a", 90.0, "m.b", 11.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.TRUE);
            assertThat(out.signals()).hasSize(2)
                    .allMatch(s -> s.state() == TriState.TRUE);
        }

        @Test
        void andOneFalse() {
            Map<String, Object> root = group("AND", List.of(
                    threshold("a", "m.a", ">=", 85),
                    threshold("b", "m.b", ">", 10)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.a", 90.0, "m.b", 5.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.FALSE);
        }

        @Test
        void orAnyTrue() {
            Map<String, Object> root = group("OR", List.of(
                    threshold("a", "m.a", ">=", 85),
                    threshold("b", "m.b", ">", 10)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.a", 10.0, "m.b", 11.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.TRUE);
        }

        @Test
        void orAllFalse() {
            Map<String, Object> root = group("OR", List.of(
                    threshold("a", "m.a", ">=", 85),
                    threshold("b", "m.b", ">", 10)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.a", 10.0, "m.b", 5.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.FALSE);
        }
    }

    // ── 嵌套混合组：A AND (B OR C) ─────────────────────────────────────────

    @Nested
    class NestedGroups {

        private Map<String, Object> mixedTree() {
            return group("AND", List.of(
                    threshold("a", "m.a", ">=", 85),
                    nestedGroup("OR", List.of(
                            threshold("b", "m.b", ">", 10),
                            threshold("c", "m.c", ">", 0)))));
        }

        @Test
        void aTrueAndInnerOrTrue() {
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(mixedTree(),
                    Map.of("m.a", 90.0, "m.b", 5.0, "m.c", 1.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.TRUE);
            // 叶子信号按树遍历顺序收集
            assertThat(out.signals()).extracting(SignalStatus::code).containsExactly("a", "b", "c");
        }

        @Test
        void aTrueButInnerOrFalse() {
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(mixedTree(),
                    Map.of("m.a", 90.0, "m.b", 5.0, "m.c", 0.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.FALSE);
        }

        @Test
        void aFalseShortCircuitsWholeTree() {
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(mixedTree(),
                    Map.of("m.a", 10.0, "m.b", 99.0, "m.c", 99.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.FALSE);
        }
    }

    // ── 三值逻辑：unknown 传播 ──────────────────────────────────────────────

    @Nested
    class UnknownPropagation {

        @Test
        void andWithUnknownAndNoFalseIsUnknown() {
            Map<String, Object> root = group("AND", List.of(
                    threshold("a", "m.a", ">=", 85),
                    threshold("b", "m.missing", ">", 10)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.a", 90.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.UNKNOWN);
        }

        @Test
        void andWithFalseWinsOverUnknown() {
            Map<String, Object> root = group("AND", List.of(
                    threshold("a", "m.a", ">=", 85),
                    threshold("b", "m.missing", ">", 10)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.a", 10.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.FALSE);
        }

        @Test
        void orWithTrueWinsOverUnknown() {
            Map<String, Object> root = group("OR", List.of(
                    threshold("a", "m.a", ">=", 85),
                    threshold("b", "m.missing", ">", 10)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.a", 90.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.TRUE);
        }

        @Test
        void orWithoutTrueButUnknownIsUnknown() {
            Map<String, Object> root = group("OR", List.of(
                    threshold("a", "m.a", ">=", 85),
                    threshold("b", "m.missing", ">", 10)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.a", 10.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.UNKNOWN);
        }

        @Test
        void missingSignalRecordedAsUnknownWithNullValue() {
            Map<String, Object> root = group("AND", List.of(threshold("a", "m.missing", ">=", 85)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root, Map.of(), Map.of());
            assertThat(out.signals()).hasSize(1);
            SignalStatus s = out.signals().get(0);
            assertThat(s.state()).isEqualTo(TriState.UNKNOWN);
            assertThat(s.currentValue()).isNull();
        }
    }

    // ── 环比条件 ────────────────────────────────────────────────────────────

    @Nested
    class RateChange {

        @Test
        void computesPercentChangeAgainstBaseline() {
            // 当前 120，1h 前 100 → +20%，条件 >= 20 成立
            Map<String, Object> root = group("AND", List.of(
                    rateChange("q", "m.qps", "1h", ">=", 20)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.qps", 120.0),
                    Map.of(ScenarioConditionEvaluator.baselineKey("m.qps", "1h"), 100.0));
            assertThat(out.state()).isEqualTo(TriState.TRUE);
            assertThat(out.signals().get(0).currentValue()).isEqualTo(20.0);
        }

        @Test
        void negativeChangeSupported() {
            // 当前 80，基准 100 → -20%，条件 < 20（环比未涨）成立
            Map<String, Object> root = group("AND", List.of(
                    rateChange("q", "m.qps", "1h", "<", 20)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.qps", 80.0),
                    Map.of(ScenarioConditionEvaluator.baselineKey("m.qps", "1h"), 100.0));
            assertThat(out.state()).isEqualTo(TriState.TRUE);
            assertThat(out.signals().get(0).currentValue()).isEqualTo(-20.0);
        }

        @Test
        void missingBaselineIsUnknown() {
            Map<String, Object> root = group("AND", List.of(
                    rateChange("q", "m.qps", "1h", ">=", 20)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.qps", 120.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.UNKNOWN);
        }

        @Test
        void zeroBaselineIsUnknown() {
            Map<String, Object> root = group("AND", List.of(
                    rateChange("q", "m.qps", "1h", ">=", 20)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root,
                    Map.of("m.qps", 120.0),
                    Map.of(ScenarioConditionEvaluator.baselineKey("m.qps", "1h"), 0.0));
            assertThat(out.state()).isEqualTo(TriState.UNKNOWN);
        }

        @Test
        void offsetMinutesParsing() {
            assertThat(ScenarioConditionEvaluator.offsetMinutes("30m")).isEqualTo(30);
            assertThat(ScenarioConditionEvaluator.offsetMinutes("1h")).isEqualTo(60);
            assertThat(ScenarioConditionEvaluator.offsetMinutes("2d")).isEqualTo(2880);
            assertThat(ScenarioConditionEvaluator.offsetMinutes(null)).isEqualTo(60);
            assertThat(ScenarioConditionEvaluator.offsetMinutes("bogus")).isEqualTo(60);
        }
    }

    // ── 边界情况 ────────────────────────────────────────────────────────────

    @Nested
    class EdgeCases {

        @Test
        void nullOrEmptyTreeIsUnknown() {
            assertThat(ScenarioConditionEvaluator.evaluate(null, Map.of(), Map.of()).state())
                    .isEqualTo(TriState.UNKNOWN);
            assertThat(ScenarioConditionEvaluator.evaluate(group("AND", List.of()), Map.of(), Map.of()).state())
                    .isEqualTo(TriState.UNKNOWN);
        }

        @Test
        void unknownOperatorNeverTriggers() {
            Map<String, Object> root = group("AND", List.of(threshold("a", "m.a", "~=", 85)));
            EvalOutcome out = ScenarioConditionEvaluator.evaluate(root, Map.of("m.a", 999.0), Map.of());
            assertThat(out.state()).isEqualTo(TriState.FALSE);
        }

        @Test
        void collectConditionsFlattensNestedTree() {
            Map<String, Object> root = group("AND", List.of(
                    threshold("a", "m.a", ">=", 85),
                    nestedGroup("OR", List.of(
                            threshold("b", "m.b", ">", 10),
                            threshold("c", "m.c", ">", 0)))));
            List<Map<String, Object>> conditions = ScenarioConditionEvaluator.collectConditions(root);
            assertThat(conditions).hasSize(3)
                    .extracting(c -> c.get("code")).containsExactly("a", "b", "c");
        }
    }

    // ── 实例级阈值覆盖 ──────────────────────────────────────────────────────

    @Nested
    class ApplyOverrides {

        @Test
        void overridesLeafThresholdWithoutMutatingTemplate() {
            Map<String, Object> cond = threshold("a", "m.a", ">=", 85);
            cond.put("exprText", "≥ 85%");
            Map<String, Object> root = group("AND", List.of(cond));

            Map<String, Object> overridden = ScenarioConditionEvaluator.applyOverrides(root, Map.of("a", 90.0));

            List<Map<String, Object>> conditions = ScenarioConditionEvaluator.collectConditions(overridden);
            assertThat(conditions.get(0).get("threshold")).isEqualTo(90.0);
            assertThat(conditions.get(0).get("exprText")).isEqualTo("≥ 90%");
            // 模板树保持不变
            assertThat(cond.get("threshold")).isEqualTo(85.0);
            assertThat(cond.get("exprText")).isEqualTo("≥ 85%");
        }

        @Test
        void overrideChangesEvaluationResult() {
            Map<String, Object> root = group("AND", List.of(threshold("a", "m.a", ">=", 85)));
            Map<String, Double> current = Map.of("m.a", 88.0);

            assertThat(ScenarioConditionEvaluator.evaluate(root, current, Map.of()).state())
                    .isEqualTo(TriState.TRUE);
            Map<String, Object> overridden = ScenarioConditionEvaluator.applyOverrides(root, Map.of("a", 90.0));
            assertThat(ScenarioConditionEvaluator.evaluate(overridden, current, Map.of()).state())
                    .isEqualTo(TriState.FALSE);
        }

        @Test
        void appliesInsideNestedGroups() {
            Map<String, Object> root = group("AND", List.of(
                    threshold("a", "m.a", ">=", 85),
                    nestedGroup("OR", List.of(threshold("b", "m.b", ">", 10)))));
            Map<String, Object> overridden = ScenarioConditionEvaluator.applyOverrides(root, Map.of("b", 20.0));
            List<Map<String, Object>> conditions = ScenarioConditionEvaluator.collectConditions(overridden);
            assertThat(conditions).extracting(c -> c.get("threshold")).containsExactly(85.0, 20.0);
        }

        @Test
        void nullOrEmptyOverridesReturnTemplateAsIs() {
            Map<String, Object> root = group("AND", List.of(threshold("a", "m.a", ">=", 85)));
            assertThat(ScenarioConditionEvaluator.applyOverrides(root, null)).isSameAs(root);
            assertThat(ScenarioConditionEvaluator.applyOverrides(root, Map.of())).isSameAs(root);
        }

        @Test
        void unknownCodeIsIgnored() {
            Map<String, Object> root = group("AND", List.of(threshold("a", "m.a", ">=", 85)));
            Map<String, Object> overridden = ScenarioConditionEvaluator.applyOverrides(root, Map.of("zzz", 1.0));
            List<Map<String, Object>> conditions = ScenarioConditionEvaluator.collectConditions(overridden);
            assertThat(conditions.get(0).get("threshold")).isEqualTo(85.0);
        }
    }

    // ── 诊断分支匹配 ────────────────────────────────────────────────────────

    @Nested
    class DiagnosisBranches {

        private List<SignalStatus> signals(TriState a, TriState b) {
            return List.of(
                    new SignalStatus("a", "A", null, "m.a", null, 1.0, a),
                    new SignalStatus("b", "B", null, "m.b", null, 2.0, b));
        }

        @Test
        void firstFullyMatchedBranchWins() {
            List<Map<String, Object>> branches = List.of(
                    Map.of("when", List.of("a", "b"), "text", "两个信号都命中"),
                    Map.of("when", List.of("a"), "text", "只命中 A"));
            String text = ScenarioConditionEvaluator.resolveDiagnosis(
                    branches, signals(TriState.TRUE, TriState.TRUE), "默认结论");
            assertThat(text).isEqualTo("两个信号都命中");
        }

        @Test
        void partialMatchFallsToNextBranch() {
            List<Map<String, Object>> branches = List.of(
                    Map.of("when", List.of("a", "b"), "text", "两个信号都命中"),
                    Map.of("when", List.of("a"), "text", "只命中 A"));
            String text = ScenarioConditionEvaluator.resolveDiagnosis(
                    branches, signals(TriState.TRUE, TriState.FALSE), "默认结论");
            assertThat(text).isEqualTo("只命中 A");
        }

        @Test
        void noBranchMatchedUsesDefault() {
            List<Map<String, Object>> branches = List.of(
                    Map.of("when", List.of("b"), "text", "只命中 B"));
            String text = ScenarioConditionEvaluator.resolveDiagnosis(
                    branches, signals(TriState.TRUE, TriState.FALSE), "默认结论");
            assertThat(text).isEqualTo("默认结论");
        }

        @Test
        void nullBranchesUseDefault() {
            String text = ScenarioConditionEvaluator.resolveDiagnosis(
                    null, signals(TriState.TRUE, TriState.TRUE), "默认结论");
            assertThat(text).isEqualTo("默认结论");
        }
    }
}
