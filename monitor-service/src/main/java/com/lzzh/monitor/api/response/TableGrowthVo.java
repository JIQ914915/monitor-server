package com.lzzh.monitor.api.response;

import java.util.List;

/** 表级周环比增长 VO（实时概况「资源 Tab」表空间列表）。 */
public class TableGrowthVo {

    /** 实例 ID。 */
    private Long instanceId;

    /** 容量指标编码（如 capacity.total_size_bytes）。 */
    private String metricCode;

    /** 表列表（按周增长字节数降序，增长最多的排前面；无上周数据的排最后）。 */
    private List<TableRow> tables;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    public List<TableRow> getTables() { return tables; }
    public void setTables(List<TableRow> tables) { this.tables = tables; }

    /** 单表周环比行。 */
    public static class TableRow {
        /** 对象名，如 {@code db_name.table_name}。 */
        private String objectName;
        /** 对象类型，如 {@code table}。 */
        private String objectType;
        /** 当前容量（字节）。 */
        private long currentBytes;
        /** 7 天前容量（字节）；null 表示 7 天内新建表。 */
        private Long prevWeekBytes;
        /** 周增长字节数；null 表示 7 天内新建表。 */
        private Long growthBytes;
        /** 周增长率（%，保留 2 位小数）；null 表示 7 天内新建表或上周大小为 0。 */
        private Double growthRatePct;

        public String getObjectName() { return objectName; }
        public void setObjectName(String objectName) { this.objectName = objectName; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public long getCurrentBytes() { return currentBytes; }
        public void setCurrentBytes(long currentBytes) { this.currentBytes = currentBytes; }
        public Long getPrevWeekBytes() { return prevWeekBytes; }
        public void setPrevWeekBytes(Long prevWeekBytes) { this.prevWeekBytes = prevWeekBytes; }
        public Long getGrowthBytes() { return growthBytes; }
        public void setGrowthBytes(Long growthBytes) { this.growthBytes = growthBytes; }
        public Double getGrowthRatePct() { return growthRatePct; }
        public void setGrowthRatePct(Double growthRatePct) { this.growthRatePct = growthRatePct; }
    }
}
