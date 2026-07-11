package com.lzzh.monitor.api.response;

/** 今日累计统计 VO（实时概况「资源 Tab」临时表 + 性能 Tab 慢查询）。 */
public class TodayStatsVo {

    /** 实例 ID。 */
    private Long instanceId;

    /** 今日创建内存临时表数（累计，日跨零点重置）。 */
    private long tmpTablesToday;

    /** 今日创建磁盘临时表数（内存超限转磁盘，越低越好）。 */
    private long tmpDiskTablesToday;

    /**
     * 今日慢查询数（超过 long_query_time 的语句累计数）。
     */
    private long slowQueriesToday;

    /** 磁盘临时表占比（tmpDiskTablesToday / tmpTablesToday，0~100；tmpTablesToday=0 时为 0）。 */
    private double diskRatioPct;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public long getTmpTablesToday() { return tmpTablesToday; }
    public void setTmpTablesToday(long tmpTablesToday) { this.tmpTablesToday = tmpTablesToday; }

    public long getTmpDiskTablesToday() { return tmpDiskTablesToday; }
    public void setTmpDiskTablesToday(long tmpDiskTablesToday) { this.tmpDiskTablesToday = tmpDiskTablesToday; }

    public long getSlowQueriesToday() { return slowQueriesToday; }
    public void setSlowQueriesToday(long slowQueriesToday) { this.slowQueriesToday = slowQueriesToday; }

    public double getDiskRatioPct() { return diskRatioPct; }
    public void setDiskRatioPct(double diskRatioPct) { this.diskRatioPct = diskRatioPct; }
}
