package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 慢 SQL 指纹聚合行 VO（时间窗口内按 digest 聚合的增量统计）。 */
@Data
@Schema(description = "慢SQL指纹聚合行")
public class SlowSqlDigestVo {

    @Schema(description = "库名（可空）")
    private String schemaName;

    @Schema(description = "SQL 指纹（performance_schema digest）")
    private String digest;

    @Schema(description = "归一化 SQL 文本（参数占位为 ?，最长 2000 字符）")
    private String digestText;

    @Schema(description = "SQL 类型：SELECT / INSERT / UPDATE / DELETE / OTHER（由 digest_text 前缀推断）")
    private String sqlType;

    @Schema(description = "窗口内执行次数")
    private Long execCount;

    @Schema(description = "窗口内总耗时（毫秒）")
    private Double totalTimeMs;

    @Schema(description = "窗口内平均单次耗时（毫秒）")
    private Double avgTimeMs;

    @Schema(description = "窗口内最慢采集周期的平均单次耗时（毫秒）")
    private Double maxAvgTimeMs;

    @Schema(description = "窗口内扫描行数")
    private Long rowsExamined;

    @Schema(description = "窗口内返回行数")
    private Long rowsSent;

    @Schema(description = "扫描/返回比（rowsExamined / rowsSent，返回 0 行时为 null）；越大说明索引效率越差")
    private Double scanRatio;

    @Schema(description = "窗口内锁等待时间（毫秒）")
    private Double lockTimeMs;

    @Schema(description = "窗口内排序行数")
    private Long sortRows;

    @Schema(description = "窗口内未使用索引的执行次数（>0 说明存在无索引执行）")
    private Long noIndexUsed;

    @Schema(description = "窗口内创建临时表数（内存+磁盘）")
    private Long tmpTables;

    @Schema(description = "窗口内创建磁盘临时表数")
    private Long tmpDiskTables;

    @Schema(description = "优化状态：字典 slow_sql_optimize_status（unoptimized/optimized），无标记默认 unoptimized")
    private String optimizeStatus;

    @Schema(description = "窗口内首次出现时间（毫秒时间戳）")
    private Long firstSeen;

    @Schema(description = "窗口内最后出现时间（毫秒时间戳）")
    private Long lastSeen;
}
