package com.lzzh.monitor.collector.alert.sms;

import java.util.Map;

/**
 * 短信供应商 SPI（§14.2：不绑定固定供应商，可项目化替换）。
 *
 * <p>新增供应商 = 新增一个本接口的 @Component 实现，{@code AlertNotificationService}
 * 按 {@code alert.notify.sms.provider} 配置路由到对应实现，无需改动通知主流程。
 * 内置实现：
 * <ul>
 *   <li>{@code aliyun} —— 阿里云短信（官方 SDK）；</li>
 *   <li>{@code http} —— 通用 HTTP 短信网关（URL + 报文模板，覆盖多数项目自建/第三方网关）。</li>
 * </ul>
 */
public interface SmsProvider {

    /** 供应商编码（与 alert.notify.sms.provider 配置值匹配，忽略大小写）。 */
    String code();

    /**
     * 发送一条告警短信。
     *
     * @param phone   目标手机号
     * @param payload 通知载荷（含 message / ruleName / instanceName / ruleLevel / kind 等键）
     */
    SmsSendResult send(String phone, Map<String, Object> payload);

    /** 发送结果（code=供应商响应码或 HTTP 状态码，body=原始响应，error=失败原因）。 */
    record SmsSendResult(boolean success, String code, String body, String error) {

        public static SmsSendResult ok(String code, String body) {
            return new SmsSendResult(true, code, body, null);
        }

        public static SmsSendResult fail(String code, String error) {
            return new SmsSendResult(false, code, null, error);
        }
    }
}
