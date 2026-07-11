package com.lzzh.monitor.collector.alert.sms;

import com.lzzh.monitor.collector.config.AlertNotifyProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 通用 HTTP 短信网关供应商（项目化适配层）：多数政企/医院项目使用自建短信平台或
 * 集成商网关，通常提供"HTTP POST + JSON/表单报文"接口。本实现通过配置即可对接，
 * 无需为每个项目写代码：
 *
 * <pre>
 * alert.notify.sms:
 *   provider: http
 *   http:
 *     url: http://sms-gateway.internal/api/send
 *     headers: { Authorization: "Bearer xxx" }
 *     body-template: '{"mobile":"{phone}","content":"[数据库监控] {ruleName}: {message}"}'
 *     content-type: application/json
 *     success-keyword: '"code":0'
 * </pre>
 *
 * 报文模板占位符：{phone} / {message} / {ruleName} / {instanceName} / {ruleLevel} / {kind}。
 * 成功判定：HTTP 2xx 且（未配置 success-keyword 或响应体包含该关键字）。
 */
@Component
public class HttpSmsProvider implements SmsProvider {

    @Resource
    private AlertNotifyProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String code() {
        return "http";
    }

    @Override
    public SmsSendResult send(String phone, Map<String, Object> payload) {
        AlertNotifyProperties.HttpSms cfg = properties.getSms().getHttp();
        if (!StringUtils.hasText(cfg.getUrl()) || !StringUtils.hasText(cfg.getBodyTemplate())) {
            return SmsSendResult.fail("CONFIG_MISSING", "HTTP 短信网关配置不完整（url / body-template 必填）");
        }
        try {
            String body = renderTemplate(cfg.getBodyTemplate(), phone, payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(cfg.getUrl()))
                    .timeout(Duration.ofSeconds(Math.max(3, cfg.getTimeoutSeconds())))
                    .header("Content-Type", StringUtils.hasText(cfg.getContentType())
                            ? cfg.getContentType() : "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (cfg.getHeaders() != null) {
                cfg.getHeaders().forEach((k, v) -> {
                    if (StringUtils.hasText(k) && v != null) {
                        builder.header(k, v);
                    }
                });
            }
            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            String respBody = resp.body();
            if (status < 200 || status >= 300) {
                return SmsSendResult.fail(String.valueOf(status), respBody);
            }
            String keyword = cfg.getSuccessKeyword();
            if (StringUtils.hasText(keyword) && (respBody == null || !respBody.contains(keyword))) {
                return SmsSendResult.fail("KEYWORD_MISS",
                        "响应未包含成功关键字 " + keyword + "：" + truncate(respBody, 500));
            }
            return SmsSendResult.ok(String.valueOf(status), respBody);
        } catch (Exception e) {
            return SmsSendResult.fail("EXCEPTION", e.getMessage());
        }
    }

    /** 占位符替换 + JSON 字符串转义（模板为 JSON 时避免消息内容中的引号破坏报文结构）。 */
    private static String renderTemplate(String template, String phone, Map<String, Object> payload) {
        String message = truncate(str(payload.get("message")), 300);
        return template
                .replace("{phone}", jsonEscape(phone))
                .replace("{message}", jsonEscape(message))
                .replace("{ruleName}", jsonEscape(str(payload.get("ruleName"))))
                .replace("{instanceName}", jsonEscape(str(payload.get("instanceName"))))
                .replace("{ruleLevel}", jsonEscape(str(payload.get("ruleLevel"))))
                .replace("{kind}", jsonEscape(str(payload.get("kind"))));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
