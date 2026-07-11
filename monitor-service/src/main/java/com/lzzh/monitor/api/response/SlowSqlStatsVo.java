package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 慢 SQL 分析页概览统计 VO（时间窗口 Top SQL 汇总 + 今日慢查询计数 + 阈值参数）。 */
@Data
@Schema(description = "慢SQL分析概览统计")
public class SlowSqlStatsVo {

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "窗口内 SQL 指纹数")
    private Long digestCount;

    @Schema(description = "窗口内总执行次数")
    private Long totalExecCount;

    @Schema(description = "窗口内总耗时（毫秒）")
    private Double totalTimeMs;

    @Schema(description = "窗口内最慢平均耗时（毫秒，单个采集周期内的 digest 平均值最大者）")
    private Double maxAvgTimeMs;

    @Schema(description = "窗口内总扫描行数")
    private Long totalRowsExamined;

    @Schema(description = "窗口内整体平均执行时间（毫秒，总耗时/总执行次数）")
    private Double avgTimeMs;

    @Schema(description = "窗口内存在无索引执行的 SQL 指纹数")
    private Long noIndexDigestCount;

    @Schema(description = "窗口内使用临时表的 SQL 指纹数")
    private Long tmpTableDigestCount;

    @Schema(description = "今日慢查询数（Slow_queries 计数器今日增量合计；无数据为 0）")
    private Long slowQueriesToday;

    @Schema(description = "慢查询阈值 long_query_time（秒）；未采集到为 null")
    private Double longQueryTimeSeconds;

    @Schema(description = "该实例是否支持 Top SQL 采集（MySQL 5.6 不支持 performance_schema digest）")
    private Boolean topSqlSupported;
}
