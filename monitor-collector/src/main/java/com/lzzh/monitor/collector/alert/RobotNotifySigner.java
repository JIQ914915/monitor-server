package com.lzzh.monitor.collector.alert;

import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * IM 机器人签名算法的纯函数实现，从 {@code AlertNotificationService} 抽取而来以便直接单测。
 *
 * <p>时间戳作为参数传入（而非内部取当前时间），使签名结果在测试中可复现、可与官方文档给出的算法逐字核对。
 */
public final class RobotNotifySigner {

    private RobotNotifySigner() {
    }

    /**
     * 钉钉自定义机器人加签（官方文档算法）：
     * {@code sign = urlEncode(base64(hmacSha256(key=secret, message=timestamp+"\n"+secret)))}。
     */
    public static String dingtalkSign(String secret, long timestampMillis) {
        String stringToSign = timestampMillis + "\n" + secret;
        byte[] signData = hmacSha256(secret.getBytes(StandardCharsets.UTF_8), stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
    }

    /** 在 webhook URL 上拼接 {@code timestamp}/{@code sign} 查询参数；secret 为空则原样返回 URL。 */
    public static String dingtalkSignedUrl(String url, String secret, long timestampMillis) {
        if (!StringUtils.hasText(secret)) {
            return url;
        }
        String sign = dingtalkSign(secret, timestampMillis);
        return url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestampMillis + "&sign=" + sign;
    }

    /**
     * 飞书自定义机器人签名校验（官方文档算法，key/message 顺序与钉钉相反）：
     * {@code sign = base64(hmacSha256(key=timestamp+"\n"+secret, message=""))}。
     */
    public static String feishuSign(String secret, long timestampSeconds) {
        String stringToSign = timestampSeconds + "\n" + secret;
        byte[] signData = hmacSha256(stringToSign.getBytes(StandardCharsets.UTF_8), new byte[0]);
        return Base64.getEncoder().encodeToString(signData);
    }

    /** IM 机器人文本消息内容：阶段前缀（触发/恢复/风暴）+ 级别 + 正文。 */
    public static String buildRobotText(String kind, String ruleLevel, String message) {
        String title = switch (kind == null ? "" : kind) {
            case "recovery" -> "【告警恢复】";
            case "storm" -> "【告警风暴】";
            default -> "【告警触发】";
        };
        String msg = message == null ? "" : message;
        return title + (StringUtils.hasText(ruleLevel) ? "[" + ruleLevel + "] " : "") + msg;
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 signing failed", e);
        }
    }
}
