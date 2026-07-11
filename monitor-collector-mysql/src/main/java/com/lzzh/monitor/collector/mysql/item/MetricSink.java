package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.spi.model.LongConnPoint;
import com.lzzh.monitor.collector.spi.model.MetricPoint;
import com.lzzh.monitor.collector.spi.model.ObjectMetricPoint;
import com.lzzh.monitor.collector.spi.model.SlowSqlSamplePoint;
import com.lzzh.monitor.collector.spi.model.TextMetricPoint;
import com.lzzh.monitor.collector.spi.model.TopSqlPoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 采集点收集器：一个采集项一次执行可向其中投递多种类型的指标点
 * （数值 / 文本覆盖变更 / 对象级 / TopSQL），由 MySqlCollector 汇总到 CollectResult。
 * <p>相比"每个类型一个方法"的设计，sink 允许一次 SQL 查询同时产出多类型点，避免重复查询
 * （如复制状态一次查询同时产出数值状态与文本错误信息）。
 */
public class MetricSink {

    private final List<MetricPoint> numeric = new ArrayList<>();
    private final List<TextMetricPoint> text = new ArrayList<>();
    private final List<ObjectMetricPoint> objects = new ArrayList<>();
    private final List<TopSqlPoint> topSql = new ArrayList<>();
    private final List<LongConnPoint> longConns = new ArrayList<>();
    private final List<SlowSqlSamplePoint> slowSqlSamples = new ArrayList<>();
    private final List<ItemError> itemErrors = new ArrayList<>();

    /** 采集项级别的失败记录（item code + 错误信息）。 */
    public record ItemError(String code, String message) {}

    /**
     * 本次采集的 SHOW GLOBAL STATUS 全量快照（GlobalStatusItem 首次读取后缓存，
     * 供同次采集中其他采集项复用，避免对目标库重复全量扫描）。
     */
    private Map<String, Long> globalStatusSnapshot;

    /** 由 GlobalStatusItem 在扫描完成后写入，供后续采集项复用。 */
    public void setGlobalStatusSnapshot(Map<String, Long> snapshot) {
        this.globalStatusSnapshot = snapshot != null ? Collections.unmodifiableMap(snapshot) : null;
    }

    /** 其他采集项（如 ThroughputItem）读取缓存的全量 Status；为 null 表示 GlobalStatusItem 尚未运行。 */
    public Map<String, Long> getGlobalStatusSnapshot() {
        return globalStatusSnapshot;
    }

    public void addNumeric(String metric, double value, long ts) {
        numeric.add(new MetricPoint(metric, value, ts));
    }

    /** 添加文本点，valueHash 自动按 SHA-256 计算。 */
    public void addText(String metric, String valueText, long ts) {
        text.add(new TextMetricPoint(metric, valueText, sha256(valueText), ts));
    }

    public void addObject(String metric, String objectType, String objectName, double value, long ts) {
        objects.add(new ObjectMetricPoint(metric, objectType, objectName, value, ts));
    }

    public void addTopSql(TopSqlPoint point) {
        topSql.add(point);
    }

    public void addLongConn(LongConnPoint point) {
        longConns.add(point);
    }

    public void addSlowSqlSample(SlowSqlSamplePoint point) {
        slowSqlSamples.add(point);
    }

    public void addItemError(String code, String message) {
        itemErrors.add(new ItemError(code, message));
    }

    public List<ItemError> getItemErrors() {
        return itemErrors;
    }

    public List<MetricPoint> numeric() {
        return numeric;
    }

    public List<TextMetricPoint> text() {
        return text;
    }

    public List<ObjectMetricPoint> objects() {
        return objects;
    }

    public List<TopSqlPoint> topSql() {
        return topSql;
    }

    public List<LongConnPoint> longConns() {
        return longConns;
    }

    public List<SlowSqlSamplePoint> slowSqlSamples() {
        return slowSqlSamples;
    }

    private static String sha256(String value) {
        String v = value == null ? "" : value;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，理论不会走到这里；兜底用 hashCode 十六进制
            return Integer.toHexString(v.hashCode());
        }
    }
}
