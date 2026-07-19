package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.spi.model.MetricPoint;
import com.lzzh.monitor.collector.spi.model.PgCollectItemStatusPoint;
import com.lzzh.monitor.collector.spi.model.ObjectMetricPoint;
import com.lzzh.monitor.collector.spi.model.PgQueryStatPoint;
import com.lzzh.monitor.collector.spi.model.PgOperationalEventPoint;
import com.lzzh.monitor.collector.spi.model.SlowSqlSamplePoint;
import com.lzzh.monitor.collector.spi.model.TextMetricPoint;
import com.lzzh.monitor.collector.spi.model.TopSqlPoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PG 采集点收集器：一个采集项一次执行可投递数值 / 文本 / 对象级指标点，
 * 由 PostgreSqlCollector 汇总到 CollectResult。
 * <p>二期起支持 TopSQL（pg_stat_statements 差值）与慢 SQL 样本（pg_stat_activity 采样）投递，
 * 与 MySQL 的 MetricSink 保持同名方法签名，便于对照维护。
 */
public class PgMetricSink {

    private final List<MetricPoint> numeric = new ArrayList<>();
    private final List<TextMetricPoint> text = new ArrayList<>();
    private final List<ObjectMetricPoint> objects = new ArrayList<>();
    private final List<TopSqlPoint> topSql = new ArrayList<>();
    private final List<PgQueryStatPoint> pgQueryStats = new ArrayList<>();
    private final List<PgOperationalEventPoint> pgOperationalEvents = new ArrayList<>();
    private final List<SlowSqlSamplePoint> slowSqlSamples = new ArrayList<>();
    private final List<PgCollectItemStatusPoint> collectItemStatuses = new ArrayList<>();
    private final Map<String, String> unavailableReasons = new HashMap<>();
    private final List<ItemError> itemErrors = new ArrayList<>();

    /** 采集项级别的失败记录（item code + 错误信息）。 */
    public record ItemError(String code, String message) {}

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

    public void addPgQueryStat(PgQueryStatPoint point) {
        pgQueryStats.add(point);
    }

    public void addOperationalEvent(PgOperationalEventPoint point) { pgOperationalEvents.add(point); }

    public List<PgOperationalEventPoint> pgOperationalEvents() { return pgOperationalEvents; }

    public List<PgQueryStatPoint> pgQueryStats() {
        return pgQueryStats;
    }

    public void addSlowSqlSample(SlowSqlSamplePoint point) {
        slowSqlSamples.add(point);
    }

    public List<TopSqlPoint> topSql() {
        return topSql;
    }

    public List<SlowSqlSamplePoint> slowSqlSamples() {
        return slowSqlSamples;
    }

    public void addItemError(String code, String message) {
        itemErrors.add(new ItemError(code, message));
    }

    public List<ItemError> getItemErrors() {
        return itemErrors;
    }

    public void addCollectItemStatus(PgCollectItemStatusPoint point) {
        collectItemStatuses.add(point);
    }

    public List<PgCollectItemStatusPoint> collectItemStatuses() {
        return collectItemStatuses;
    }

    public void markUnavailable(String itemCode, String reason) {
        unavailableReasons.put(itemCode, reason);
    }

    public String unavailableReason(String itemCode) {
        return unavailableReasons.get(itemCode);
    }

    /** 当前已收集的全部业务点数量，用于计算单个采集项的实际产出。 */
    public int pointCount() {
        return numeric.size() + text.size() + objects.size() + topSql.size()
                + pgQueryStats.size() + pgOperationalEvents.size() + slowSqlSamples.size();
    }

    public int errorCount() {
        return itemErrors.size();
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
