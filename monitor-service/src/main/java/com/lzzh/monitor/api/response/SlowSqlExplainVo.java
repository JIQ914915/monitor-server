package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 慢 SQL 实时执行计划结果（目标库 EXPLAIN 输出，按列名+行值透传）。 */
@Data
public class SlowSqlExplainVo {

    @Schema(description = "EXPLAIN 结果列名（如 id/select_type/table/type/key/rows/Extra）")
    private List<String> columns;

    @Schema(description = "EXPLAIN 结果行，每行与 columns 对齐，值统一转字符串（NULL 为 null）")
    private List<List<String>> rows;
}
