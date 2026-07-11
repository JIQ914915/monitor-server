package com.lzzh.monitor.collector.alert;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RobotNotifySigner} 单元测试。
 *
 * <p>钉钉/飞书签名算法极易在"HMAC key 与被签名内容顺序"上出错（两者恰好相反），
 * 因此测试里按官方文档字面重新独立实现一份参照算法，与生产代码逐字节比对，
 * 而不是仅断言"两次调用结果相同"这类弱校验，以尽量在重构时捕获此类回归。
 */
class RobotNotifySignerTest {

    @Test
    void dingtalkSignMatchesReferenceImplementation() throws Exception {
        String secret = "SEC-this-is-secret";
        long timestamp = 1_700_000_000_000L;

        String expected = referenceDingtalkSign(secret, timestamp);
        assertThat(RobotNotifySigner.dingtalkSign(secret, timestamp)).isEqualTo(expected);
    }

    @Test
    void dingtalkSignedUrlAppendsTimestampAndSignAsQueryParams() {
        String url = "https://oapi.dingtalk.com/robot/send?access_token=abc123";
        long timestamp = 1_700_000_000_000L;
        String secret = "SEC-demo";

        String signedUrl = RobotNotifySigner.dingtalkSignedUrl(url, secret, timestamp);

        assertThat(signedUrl)
                .startsWith(url + "&timestamp=" + timestamp + "&sign=")
                .contains("&sign=");
    }

    @Test
    void dingtalkSignedUrlUsesQuestionMarkWhenUrlHasNoExistingQuery() {
        String url = "https://oapi.dingtalk.com/robot/send";
        String signedUrl = RobotNotifySigner.dingtalkSignedUrl(url, "SEC-demo", 1L);
        assertThat(signedUrl).startsWith(url + "?timestamp=1&sign=");
    }

    @Test
    void dingtalkSignedUrlReturnsPlainUrlWhenSecretBlank() {
        String url = "https://oapi.dingtalk.com/robot/send?access_token=abc123";
        assertThat(RobotNotifySigner.dingtalkSignedUrl(url, null, 1L)).isEqualTo(url);
        assertThat(RobotNotifySigner.dingtalkSignedUrl(url, "  ", 1L)).isEqualTo(url);
    }

    @Test
    void feishuSignMatchesReferenceImplementation() throws Exception {
        String secret = "feishu-secret";
        long timestampSeconds = 1_700_000_000L;

        String expected = referenceFeishuSign(secret, timestampSeconds);
        assertThat(RobotNotifySigner.feishuSign(secret, timestampSeconds)).isEqualTo(expected);
    }

    @Test
    void feishuAndDingtalkSignaturesDifferForSameInput_becauseKeyMessageOrderIsSwapped() {
        String secret = "same-secret";
        long ts = 1_700_000_000L;
        // 钉钉：key=secret, message=timestamp+"\n"+secret；飞书：key=timestamp+"\n"+secret, message=""
        // 两者互为不同算法，用同样输入产生的签名必须不同，用于防止实现时把两者算法弄混。
        assertThat(RobotNotifySigner.dingtalkSign(secret, ts))
                .isNotEqualTo(RobotNotifySigner.feishuSign(secret, ts));
    }

    @Test
    void buildRobotTextPrependsPhasePrefixAndLevel() {
        assertThat(RobotNotifySigner.buildRobotText("trigger", "level_1", "实例CPU过高"))
                .isEqualTo("【告警触发】[level_1] 实例CPU过高");
        assertThat(RobotNotifySigner.buildRobotText("recovery", null, "已恢复"))
                .isEqualTo("【告警恢复】已恢复");
        assertThat(RobotNotifySigner.buildRobotText("storm", "level_2", "风暴摘要"))
                .isEqualTo("【告警风暴】[level_2] 风暴摘要");
    }

    @Test
    void buildRobotTextToleratesNullMessage() {
        assertThat(RobotNotifySigner.buildRobotText("trigger", null, null))
                .isEqualTo("【告警触发】");
    }

    /** 逐字转录钉钉开放平台《自定义机器人安全设置》文档的 Java 示例算法。 */
    private static String referenceDingtalkSign(String secret, long timestamp) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
    }

    /** 逐字转录飞书开放平台《自定义机器人使用指南》文档的签名算法（key/message 顺序与钉钉相反）。 */
    private static String referenceFeishuSign(String secret, long timestampSeconds) throws Exception {
        String stringToSign = timestampSeconds + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(new byte[]{});
        return Base64.getEncoder().encodeToString(signData);
    }
}
