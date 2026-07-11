package com.lzzh.monitor.collector.host;

import com.lzzh.monitor.collector.spi.model.MetricPoint;
import com.lzzh.monitor.collector.spi.model.TextMetricPoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** 主机采集结果收集器（数值/文本点 + 采集项错误），单次采集内使用，非线程安全。 */
public class HostMetricSink {

    private final List<MetricPoint> numeric = new ArrayList<>();
    private final List<TextMetricPoint> text = new ArrayList<>();
    private final List<String> itemErrors = new ArrayList<>();

    public void addNumeric(String metric, double value, long ts) {
        numeric.add(new MetricPoint(metric, value, ts));
    }

    /** 添加文本点，valueHash 自动按 SHA-256 计算（写入层据此做变更检测）。 */
    public void addText(String metric, String valueText, long ts) {
        text.add(new TextMetricPoint(metric, valueText, sha256(valueText), ts));
    }

    public void addItemError(String code, String message) {
        itemErrors.add(code + ": " + message);
    }

    public List<MetricPoint> numeric() {
        return numeric;
    }

    public List<TextMetricPoint> text() {
        return text;
    }

    public List<String> itemErrors() {
        return itemErrors;
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，理论不可达
            throw new IllegalStateException(e);
        }
    }
}
