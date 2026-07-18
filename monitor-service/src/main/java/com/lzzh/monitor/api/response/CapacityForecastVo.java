package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 容量预测 VO：基于库表容量日均增长 + 主机数据盘剩余空间，线性外推预计剩余可用天数。
 * <p>估算口径：日均增长取最近 N 天日级容量快照的线性增量（(末值-首值)/天数）；
 * 剩余空间取优先取实例关联主机数据盘；无主机数据时取 SQL Server 文件卷中剩余比例最低的卷。
 */
@Data
@Schema(description = "实例容量预测（预计剩余可用天数）")
public class CapacityForecastVo {

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "最近一日库表总容量（字节）；null 表示暂无容量快照")
    private Long currentBytes;

    @Schema(description = "日均增长（字节/天，线性估算，可能为负）；null 表示样本不足")
    private Double dailyGrowthBytes;

    @Schema(description = "参与估算的样本时间跨度（天）")
    private Integer sampleDays;

    @Schema(description = "数据盘挂载点（关联主机数据盘或 SQL Server 约束卷）；null 表示未关联主机或无磁盘数据")
    private String diskMount;

    @Schema(description = "数据盘总容量（字节）")
    private Long diskTotalBytes;

    @Schema(description = "数据盘剩余可用（字节）")
    private Long diskAvailBytes;

    @Schema(description = "数据盘使用率（%）")
    private Double diskUsagePercent;

    @Schema(description = "预计剩余可用天数（剩余空间 / 日均增长，上限 3650）；null 表示无法估算（无增长/无磁盘数据/样本不足）")
    private Integer estimatedDaysRemaining;

    @Schema(description = "估算结论或无法估算原因（面向用户的一句话说明）")
    private String note;

    @Schema(description = "预测状态字典 capacity_prediction_status：stable/risk/insufficient")
    private String predictionStatus;

    @Schema(description = "近7天日均增长（字节/天）")
    private Double dailyGrowth7dBytes;

    @Schema(description = "近30天日均增长（字节/天）")
    private Double dailyGrowth30dBytes;

    @Schema(description = "预计耗尽日期（yyyy-MM-dd）")
    private String estimatedExhaustionDate;
}
