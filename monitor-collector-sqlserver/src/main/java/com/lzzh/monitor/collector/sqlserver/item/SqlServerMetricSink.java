package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.MetricPoint;
import com.lzzh.monitor.collector.spi.model.ObjectMetricPoint;
import com.lzzh.monitor.collector.spi.model.SqlServerDiagnosticEventPoint;
import com.lzzh.monitor.collector.spi.model.TextMetricPoint;
import com.lzzh.monitor.collector.spi.model.TopSqlPoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** SQL Server 数值、文本和采集项错误汇总。 */
public class SqlServerMetricSink {
    public record ItemError(String code, String message) {}

    private final List<MetricPoint> numeric = new ArrayList<>();
    private final List<ObjectMetricPoint> objects = new ArrayList<>();
    private final List<TextMetricPoint> text = new ArrayList<>();
    private final List<TopSqlPoint> topSql = new ArrayList<>();
    private final List<SqlServerDiagnosticEventPoint> diagnosticEvents = new ArrayList<>();
    private final List<ItemError> errors = new ArrayList<>();

    public void addNumeric(String metric, double value, long ts) {
        numeric.add(new MetricPoint(metric, value, ts));
    }

    public void addText(String metric, String value, long ts) {
        String safe = value == null ? "" : value;
        text.add(new TextMetricPoint(metric, safe, sha256(safe), ts));
    }

    public void addObject(String metric,String type,String name,double value,long ts) {
        objects.add(new ObjectMetricPoint(metric,type,name,value,ts));
    }

    public void addTopSql(TopSqlPoint point) { topSql.add(point); }

    public void addDiagnosticEvent(SqlServerDiagnosticEventPoint point) { diagnosticEvents.add(point); }

    public void addItemError(String code, String message) {
        errors.add(new ItemError(code, message));
    }

    public List<MetricPoint> numeric() { return numeric; }
    public List<ObjectMetricPoint> objects() { return objects; }
    public List<TextMetricPoint> text() { return text; }
    public List<TopSqlPoint> topSql() { return topSql; }
    public List<SqlServerDiagnosticEventPoint> diagnosticEvents() { return diagnosticEvents; }
    public List<ItemError> errors() { return errors; }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                result.append(Character.forDigit((b >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(b & 0x0f, 16));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }
}
