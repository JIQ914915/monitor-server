package com.lzzh.monitor.collector.alert.sms;

import cn.hutool.json.JSONUtil;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.lzzh.monitor.collector.config.AlertNotifyProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/** 阿里云短信供应商（官方 Tea OpenAPI SDK），逻辑自 AlertNotificationService 平移。 */
@Component
public class AliyunSmsProvider implements SmsProvider {

    @Resource
    private AlertNotifyProperties properties;

    @Override
    public String code() {
        return "aliyun";
    }

    @Override
    public SmsSendResult send(String phone, Map<String, Object> payload) {
        AlertNotifyProperties.Aliyun aliyun = properties.getSms().getAliyun();
        if (!StringUtils.hasText(aliyun.getAccessKeyId())
                || !StringUtils.hasText(aliyun.getAccessKeySecret())
                || !StringUtils.hasText(aliyun.getSignName())
                || !StringUtils.hasText(aliyun.getTemplateCode())) {
            return SmsSendResult.fail("CONFIG_MISSING", "阿里短信配置不完整");
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(aliyun.getAccessKeyId())
                    .setAccessKeySecret(aliyun.getAccessKeySecret())
                    .setEndpoint(aliyun.getEndpoint());
            com.aliyun.dysmsapi20170525.Client client = new com.aliyun.dysmsapi20170525.Client(config);
            SendSmsRequest req = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(aliyun.getSignName())
                    .setTemplateCode(aliyun.getTemplateCode())
                    .setTemplateParam(JSONUtil.toJsonStr(Map.of(
                            "message", truncate(nullSafe((String) payload.get("message")), 200),
                            "ruleName", nullSafe((String) payload.get("ruleName")),
                            "instanceName", nullSafe((String) payload.get("instanceName"))
                    )));
            var resp = client.sendSms(req);
            String code = resp.getBody() == null ? "" : resp.getBody().getCode();
            String body = JSONUtil.toJsonStr(resp.getBody());
            if ("OK".equalsIgnoreCase(code)) {
                return SmsSendResult.ok(code, body);
            }
            return SmsSendResult.fail(code, body);
        } catch (Exception e) {
            return SmsSendResult.fail("EXCEPTION", e.getMessage());
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
