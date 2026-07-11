package com.lzzh.monitor.api.response;

import java.util.List;

/** 性能分析多指标趋势批量查询响应 VO。 */
public class PerfTrendBatchVo {

    /** 实例 ID。 */
    private Long instanceId;

    /** 实际生效的数据频率：1m / 1h。 */
    private String frequency;

    /** 各指标趋势序列（顺序与请求 metricCodes 一致；无数据的指标 points 为空列表）。 */
    private List<Series> series;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public List<Series> getSeries() { return series; }
    public void setSeries(List<Series> series) { this.series = series; }

    /** 单指标趋势序列。 */
    public static class Series {
        /** 指标编码，如 {@code mysql.qps}。 */
        private String metricCode;
        /** 趋势点列表（按时间升序）。 */
        private List<MetricTrendVo.Point> points;

        public Series() {}
        public Series(String metricCode, List<MetricTrendVo.Point> points) {
            this.metricCode = metricCode;
            this.points = points;
        }

        public String getMetricCode() { return metricCode; }
        public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
        public List<MetricTrendVo.Point> getPoints() { return points; }
        public void setPoints(List<MetricTrendVo.Point> points) { this.points = points; }
    }
}
