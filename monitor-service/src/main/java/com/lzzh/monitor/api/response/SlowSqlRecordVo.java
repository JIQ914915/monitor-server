package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 慢 SQL 采集周期明细行 VO（某 digest 在某个采集周期内的增量）。 */
@Data
@Schema(description = "慢SQL采集周期明细行")
public class SlowSqlRecordVo {

    @Schema(description = "采集时间（毫秒时间戳）")
    private Long collectTime;

    @Schema(description = "库名（可空）")
    private String schemaName;

    @Schema(description = "SQL 指纹")
    private String digest;

    @Schema(description = "归一化 SQL 文本")
    private String digestText;

    @Schema(description = "SQL 类型：SELECT / INSERT / UPDATE / DELETE / OTHER")
    private String sqlType;

    @Schema(description = "该周期执行次数")
    private Long execCount;

    @Schema(description = "该周期平均单次耗时（毫秒）")
    private Double avgTimeMs;

    @Schema(description = "该周期总耗时（毫秒）")
    private Double totalTimeMs;

    @Schema(description = "该周期扫描行数")
    private Long rowsExamined;

    @Schema(description = "该周期返回行数")
    private Long rowsSent;

    @Schema(description = "该周期锁等待时间（毫秒）")
    private Double lockTimeMs;

    @Schema(description = "该周期排序行数")
    private Long sortRows;

    @Schema(description = "该周期未使用索引的执行次数")
    private Long noIndexUsed;

    @Schema(description = "该周期创建临时表数（内存+磁盘）")
    private Long tmpTables;

    @Schema(description = "该周期创建磁盘临时表数")
    private Long tmpDiskTables;
}
