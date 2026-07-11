package com.lzzh.monitor.collector.alert;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlertConditionEvaluator} 单元测试：覆盖告警评估中最容易出错、影响面最大的
 * 阈值比较 / 运算符反义 / 恢复判定 / 持续时间解析 / 归并去重键构造逻辑。
 */
class AlertConditionEvaluatorTest {

    @Nested
    class Compare {

        @Test
        void greaterThan() {
            assertThat(AlertConditionEvaluator.compare(91, ">", 90)).isTrue();
            assertThat(AlertConditionEvaluator.compare(90, ">", 90)).isFalse();
            assertThat(AlertConditionEvaluator.compare(89, ">", 90)).isFalse();
        }

        @Test
        void greaterThanOrEqual() {
            assertThat(AlertConditionEvaluator.compare(90, ">=", 90)).isTrue();
            assertThat(AlertConditionEvaluator.compare(89.99, ">=", 90)).isFalse();
        }

        @Test
        void lessThan() {
            assertThat(AlertConditionEvaluator.compare(89, "<", 90)).isTrue();
            assertThat(AlertConditionEvaluator.compare(90, "<", 90)).isFalse();
        }

        @Test
        void lessThanOrEqual() {
            assertThat(AlertConditionEvaluator.compare(90, "<=", 90)).isTrue();
            assertThat(AlertConditionEvaluator.compare(90.01, "<=", 90)).isFalse();
        }

        @Test
        void equalsAcceptsBothTokens() {
            assertThat(AlertConditionEvaluator.compare(90, "=", 90)).isTrue();
            assertThat(AlertConditionEvaluator.compare(90, "==", 90)).isTrue();
            assertThat(AlertConditionEvaluator.compare(90.5, "=", 90)).isFalse();
        }

        @Test
        void notEquals() {
            assertThat(AlertConditionEvaluator.compare(91, "!=", 90)).isTrue();
            assertThat(AlertConditionEvaluator.compare(90, "!=", 90)).isFalse();
        }

        @Test
        void unknownOrNullOperatorNeverTriggers() {
            assertThat(AlertConditionEvaluator.compare(999, "~=", 90)).isFalse();
            assertThat(AlertConditionEvaluator.compare(999, null, 90)).isFalse();
        }
    }

    @Nested
    class InverseOperator {

        @Test
        void invertsEachComparisonOperator() {
            assertThat(AlertConditionEvaluator.inverseOperator(">")).isEqualTo("<=");
            assertThat(AlertConditionEvaluator.inverseOperator(">=")).isEqualTo("<");
            assertThat(AlertConditionEvaluator.inverseOperator("<")).isEqualTo(">=");
            assertThat(AlertConditionEvaluator.inverseOperator("<=")).isEqualTo(">");
        }

        @Test
        void unknownOrNullFallsBackToEquals() {
            assertThat(AlertConditionEvaluator.inverseOperator("=")).isEqualTo("==");
            assertThat(AlertConditionEvaluator.inverseOperator(null)).isEqualTo("==");
        }
    }

    @Nested
    class CheckCondition {

        @Test
        void triggersWhenThresholdCrossed() {
            Map<String, Object> cfg = Map.of("operator", ">", "threshold", 80.0);
            assertThat(AlertConditionEvaluator.checkCondition(cfg, 85)).isTrue();
            assertThat(AlertConditionEvaluator.checkCondition(cfg, 75)).isFalse();
        }

        @Test
        void integerThresholdIsAccepted() {
            // 前端/存储可能把 threshold 存成整数（Integer），不应因数值类型差异而判断失败
            Map<String, Object> cfg = Map.of("operator", ">=", "threshold", 90);
            assertThat(AlertConditionEvaluator.checkCondition(cfg, 90)).isTrue();
        }

        @Test
        void missingOperatorOrThresholdNeverTriggers() {
            assertThat(AlertConditionEvaluator.checkCondition(null, 100)).isFalse();
            assertThat(AlertConditionEvaluator.checkCondition(Map.of("operator", ">"), 100)).isFalse();
            assertThat(AlertConditionEvaluator.checkCondition(Map.of("threshold", 1.0), 100)).isFalse();
        }
    }

    @Nested
    class CheckRecovery {

        @Test
        void usesExplicitRecoveryConfigWhenPresent() {
            Map<String, Object> condition = Map.of("operator", ">", "threshold", 90.0);
            Map<String, Object> recovery = Map.of("operator", "<", "threshold", 70.0);
            // 显式恢复条件优先：85 既不满足恢复(<70)也不满足触发反义(<=90)的差异场景，验证走的是显式配置
            assertThat(AlertConditionEvaluator.checkRecovery(condition, recovery, 65)).isTrue();
            assertThat(AlertConditionEvaluator.checkRecovery(condition, recovery, 80)).isFalse();
        }

        @Test
        void fallsBackToInverseOfTriggerConditionWhenRecoveryConfigAbsent() {
            Map<String, Object> condition = Map.of("operator", ">", "threshold", 90.0);
            // 触发条件为 >90，无显式恢复配置时，恢复条件应为其反义 <=90
            assertThat(AlertConditionEvaluator.checkRecovery(condition, null, 90)).isTrue();
            assertThat(AlertConditionEvaluator.checkRecovery(condition, Map.of(), 91)).isFalse();
        }

        @Test
        void nullConditionNeverRecovers() {
            assertThat(AlertConditionEvaluator.checkRecovery(null, null, 0)).isFalse();
        }
    }

    @Nested
    class ReadDurationSeconds {

        @Test
        void readsNumericValue() {
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("duration", 120);
            assertThat(AlertConditionEvaluator.readDurationSeconds(cfg, "duration")).isEqualTo(120);
        }

        @Test
        void parsesStringValue() {
            Map<String, Object> cfg = Map.of("duration", "60");
            assertThat(AlertConditionEvaluator.readDurationSeconds(cfg, "duration")).isEqualTo(60);
        }

        @Test
        void invalidOrMissingValueDefaultsToZero() {
            assertThat(AlertConditionEvaluator.readDurationSeconds(null, "duration")).isZero();
            assertThat(AlertConditionEvaluator.readDurationSeconds(Map.of(), "duration")).isZero();
            assertThat(AlertConditionEvaluator.readDurationSeconds(Map.of("duration", "not-a-number"), "duration")).isZero();
        }

        @Test
        void negativeNumberClampedToZero() {
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("duration", -30);
            assertThat(AlertConditionEvaluator.readDurationSeconds(cfg, "duration")).isZero();
        }
    }

    @Nested
    class BuildDedupKey {

        @Test
        void withoutDimensionKeyOnlyHasTwoSegments() {
            assertThat(AlertConditionEvaluator.buildDedupKey("cpu.high", 100L, null))
                    .isEqualTo("cpu.high:100");
        }

        @Test
        void withDimensionKeyAppendsThirdSegment() {
            assertThat(AlertConditionEvaluator.buildDedupKey("slow.sql", 100L, "digest-abc"))
                    .isEqualTo("slow.sql:100:digest-abc");
        }

        @Test
        void blankDimensionKeyTreatedAsAbsent() {
            assertThat(AlertConditionEvaluator.buildDedupKey("cpu.high", 100L, "  "))
                    .isEqualTo("cpu.high:100");
        }

        @Test
        void nullRuleCodeDoesNotThrow() {
            assertThat(AlertConditionEvaluator.buildDedupKey(null, 100L, null)).isEqualTo(":100");
        }
    }
}
