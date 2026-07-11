package com.lzzh.monitor.api.response;

import java.util.List;

/** 对象级指标查询响应 VO（如表容量 Top N、连接来源 Top N）。 */
public class MetricObjectVo {

    /** 实例 ID。 */
    private Long instanceId;

    /** 查询的指标编码，如 {@code capacity.total_size_bytes}。 */
    private String metricCode;

    /** 对象列表（按 value 降序，即 Top N）。 */
    private List<Item> items;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    /** 单对象条目。 */
    public static class Item {
        /** 对象名，如 `db_name.table_name` 或 `host:port`。 */
        private String objectName;
        /** 对象类型，如 {@code table}、{@code source}。 */
        private String objectType;
        /** 指标值（单位由指标定义决定，通常为 Bytes 或 Count）。 */
        private double value;
        /** 采集时间（毫秒时间戳，UTC）。 */
        private long collectTimeMs;

        public String getObjectName() { return objectName; }
        public void setObjectName(String objectName) { this.objectName = objectName; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        public long getCollectTimeMs() { return collectTimeMs; }
        public void setCollectTimeMs(long collectTimeMs) { this.collectTimeMs = collectTimeMs; }
    }
}
