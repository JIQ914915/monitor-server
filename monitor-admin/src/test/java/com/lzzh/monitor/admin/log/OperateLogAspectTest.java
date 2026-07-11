package com.lzzh.monitor.admin.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OperateLogAspect#maskSensitive} 脱敏逻辑单测。
 *
 * <p>重点覆盖此前"字段名需与关键词完全相等"导致的绕过场景：
 * dingtalkSecret/feishuSecret/connPassword 等复合命名的敏感字段。
 */
class OperateLogAspectTest {

    @Test
    void masksExactSensitiveKeys() {
        String input = "{\"password\":\"p@ss\",\"username\":\"admin\"}";
        String masked = OperateLogAspect.maskSensitive(input);
        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"username\":\"admin\"");
    }

    @Test
    void masksCompositeFieldNamesContainingSensitiveSubstring() {
        String input = "{\"dingtalkSecret\":\"SEC123\",\"feishuSecret\":\"SEC456\","
                + "\"connPassword\":\"pwd\",\"wecomAccessToken\":\"tok\"}";
        String masked = OperateLogAspect.maskSensitive(input);
        assertThat(masked)
                .contains("\"dingtalkSecret\":\"***\"")
                .contains("\"feishuSecret\":\"***\"")
                .contains("\"connPassword\":\"***\"")
                .contains("\"wecomAccessToken\":\"***\"");
    }

    @Test
    void isCaseInsensitive() {
        String input = "{\"DingtalkSECRET\":\"SEC123\"}";
        String masked = OperateLogAspect.maskSensitive(input);
        assertThat(masked).contains("\"DingtalkSECRET\":\"***\"");
    }

    @Test
    void leavesNonSensitiveFieldsUntouched() {
        String input = "{\"instanceName\":\"prod-mysql\",\"remark\":\"no secrets here\"}";
        String masked = OperateLogAspect.maskSensitive(input);
        assertThat(masked).isEqualTo(input);
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(OperateLogAspect.maskSensitive(null)).isNull();
        assertThat(OperateLogAspect.maskSensitive("")).isEmpty();
    }
}
