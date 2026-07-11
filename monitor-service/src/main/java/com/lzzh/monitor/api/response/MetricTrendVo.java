package com.lzzh.monitor.api.response;

import java.util.List;

/** 时序指标趋势响应 VO（单指标）。 */
public class MetricTrendVo {

    /** 实例 ID。 */
    private Long instanceId;

    /** 指标编码，如 {@code mysql.qps}。 */
    private String metricCode;

    /** 趋势点列表（按时间升序）。 */
    private List<Point> points;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    public List<Point> getPoints() { return points; }
    public void setPoints(List<Point> points) { this.points = points; }

    /** 单趋势点：时间戳（毫秒）+ 值。 */
    public static class Point {
        /** 采集时间（毫秒时间戳，UTC）。 */
        private long ts;
        /** 指标值。 */
        private double value;

        public Point() {}
        public Point(long ts, double value) { this.ts = ts; this.value = value; }

        public long getTs() { return ts; }
        public void setTs(long ts) { this.ts = ts; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
    }
}
