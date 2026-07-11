package com.lzzh.monitor.api.response;

import java.util.List;
import java.util.Map;

/** 文本指标查询响应 VO。 */
public class MetricTextVo {

    /** 实例 ID。 */
    private Long instanceId;

    /**
     * 文本指标最新值 Map（批量查询模式）。
     * <p>key：指标编码（如 mysql.var_text.sql_mode）；
     * value：最新文本值（null 表示新鲜窗口内无数据）。
     */
    private Map<String, String> values;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public Map<String, String> getValues() { return values; }
    public void setValues(Map<String, String> values) { this.values = values; }

    /** 文本指标变更历史单条 VO（历史查询模式）。 */
    public static class HistoryItem {
        /** 指标编码。 */
        private String metricCode;
        /** 文本值。 */
        private String valueText;
        /** 采集时间（毫秒时间戳，UTC）。 */
        private long collectTimeMs;

        public String getMetricCode() { return metricCode; }
        public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
        public String getValueText() { return valueText; }
        public void setValueText(String valueText) { this.valueText = valueText; }
        public long getCollectTimeMs() { return collectTimeMs; }
        public void setCollectTimeMs(long collectTimeMs) { this.collectTimeMs = collectTimeMs; }
    }

    /** 文本指标变更历史响应 VO（单指标历史模式）。 */
    public static class HistoryVo {
        private Long instanceId;
        private String metricCode;
        private List<HistoryItem> history;

        public Long getInstanceId() { return instanceId; }
        public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
        public String getMetricCode() { return metricCode; }
        public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
        public List<HistoryItem> getHistory() { return history; }
        public void setHistory(List<HistoryItem> history) { this.history = history; }
    }
}
