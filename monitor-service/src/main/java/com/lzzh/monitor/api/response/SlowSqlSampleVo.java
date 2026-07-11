package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 慢 SQL 真实执行样本行 VO（一行 = 一次真实执行，SQL 含参数原文）。 */
@Data
@Schema(description = "慢SQL真实执行样本行")
public class SlowSqlSampleVo {

    @Schema(description = "去重键：threadId-eventId（前端行 key 使用）")
    private String sampleKey;

    @Schema(description = "执行账号")
    private String connUser;

    @Schema(description = "来源主机")
    private String connHost;

    @Schema(description = "库名（可空）")
    private String schemaName;

    @Schema(description = "SQL 指纹（关联指纹分析，可空）")
    private String digest;

    @Schema(description = "真实执行 SQL（含参数，截断 4000 字符）")
    private String sqlText;

    @Schema(description = "SQL 类型：SELECT / INSERT / UPDATE / DELETE / OTHER")
    private String sqlType;

    @Schema(description = "执行耗时（毫秒）")
    private Double execTimeMs;

    @Schema(description = "锁等待（毫秒）")
    private Double lockTimeMs;

    @Schema(description = "扫描行数")
    private Long rowsExamined;

    @Schema(description = "返回行数")
    private Long rowsSent;

    @Schema(description = "排序行数")
    private Long sortRows;

    @Schema(description = "本次执行是否未使用索引")
    private Boolean noIndexUsed;

    @Schema(description = "创建临时表数（内存+磁盘）")
    private Long tmpTables;

    @Schema(description = "创建磁盘临时表数")
    private Long tmpDiskTables;

    @Schema(description = "采集时间（毫秒时间戳，近似执行结束时间）")
    private Long collectTime;
}
