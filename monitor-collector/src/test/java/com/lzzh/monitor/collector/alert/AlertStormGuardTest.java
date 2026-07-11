package com.lzzh.monitor.collector.alert;

import com.lzzh.monitor.collector.config.AlertNotifyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link AlertStormGuard} 单元测试：验证滑动窗口下的放行/摘要/抑制三态判定。 */
class AlertStormGuardTest {

    private AlertNotifyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AlertNotifyProperties();
    }

    @Test
    void allowsUpToThresholdWithinWindow() {
        properties.getStorm().setThreshold(3);
        properties.getStorm().setWindowMinutes(5);
        AlertStormGuard guard = new AlertStormGuard();
        ReflectionTestUtils.setField(guard, "properties", properties);

        for (int i = 0; i < 3; i++) {
            assertThat(guard.decide(1L).kind()).isEqualTo(AlertStormGuard.Kind.ALLOW);
        }
    }

    @Test
    void suppressesAfterThresholdExceededWithinDigestInterval() {
        properties.getStorm().setThreshold(2);
        properties.getStorm().setWindowMinutes(5);
        properties.getStorm().setDigestIntervalMinutes(10);
        AlertStormGuard guard = new AlertStormGuard();
        ReflectionTestUtils.setField(guard, "properties", properties);

        assertThat(guard.decide(1L).kind()).isEqualTo(AlertStormGuard.Kind.ALLOW);
        assertThat(guard.decide(1L).kind()).isEqualTo(AlertStormGuard.Kind.ALLOW);
        // 第 3 次已超过阈值 2，且首次摘要间隔尚未到达，应为 DIGEST（首次超限立即摘要）或 SUPPRESS。
        // lastDigestMs 初始为 0，now - 0 必然 >= digestIntervalMs，因此首次超限即触发 DIGEST。
        AlertStormGuard.Decision third = guard.decide(1L);
        assertThat(third.kind()).isEqualTo(AlertStormGuard.Kind.DIGEST);
        assertThat(third.suppressedCount()).isEqualTo(1);

        // 摘要刚发送，短时间内再次超限应被 SUPPRESS（未到下一次摘要间隔）。
        AlertStormGuard.Decision fourth = guard.decide(1L);
        assertThat(fourth.kind()).isEqualTo(AlertStormGuard.Kind.SUPPRESS);
        assertThat(fourth.suppressedCount()).isEqualTo(1);
    }

    @Test
    void thresholdZeroOrNegativeDisablesGuardAlwaysAllow() {
        properties.getStorm().setThreshold(0);
        AlertStormGuard guard = new AlertStormGuard();
        ReflectionTestUtils.setField(guard, "properties", properties);
        for (int i = 0; i < 50; i++) {
            assertThat(guard.decide(1L).kind()).isEqualTo(AlertStormGuard.Kind.ALLOW);
        }
    }

    @Test
    void nullInstanceIdAlwaysAllowed() {
        properties.getStorm().setThreshold(1);
        AlertStormGuard guard = new AlertStormGuard();
        ReflectionTestUtils.setField(guard, "properties", properties);
        assertThat(guard.decide(null).kind()).isEqualTo(AlertStormGuard.Kind.ALLOW);
    }

    @Test
    void differentInstancesTrackedIndependently() {
        properties.getStorm().setThreshold(1);
        AlertStormGuard guard = new AlertStormGuard();
        ReflectionTestUtils.setField(guard, "properties", properties);

        assertThat(guard.decide(1L).kind()).isEqualTo(AlertStormGuard.Kind.ALLOW);
        // 实例 1 已达阈值应进入摘要/抑制，但实例 2 是全新窗口，应仍被放行
        assertThat(guard.decide(2L).kind()).isEqualTo(AlertStormGuard.Kind.ALLOW);
    }
}
