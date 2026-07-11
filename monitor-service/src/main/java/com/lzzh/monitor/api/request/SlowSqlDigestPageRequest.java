package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 慢 SQL 指纹聚合分页查询入参。
 * <p>基于 metric_top_sql（小时级 digest 周期增量）按 (schema_name, digest) 聚合时间窗口内增量，
 * 输出窗口内 Top SQL 排名列表。
 */
@Data
@Schema(description = "慢SQL指纹聚合分页查询入参")
public class SlowSqlDigestPageRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "开始时间（毫秒时间戳，含）；不传默认为 now-24h", example = "1751500000000")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳，含）；不传默认为 now", example = "1751586400000")
    private Long to;

    @Schema(description = "SQL 文本关键词（模糊匹配 digest_text，忽略大小写）")
    @Size(max = 200, message = "keyword 最长 200 字符")
    private String keyword;

    @Schema(description = "库名（精确匹配 schema_name）")
    @Size(max = 128, message = "schemaName 最长 128 字符")
    private String schemaName;

    @Schema(description = "SQL 类型过滤：SELECT / INSERT / UPDATE / DELETE，不传为全部",
            allowableValues = {"SELECT", "INSERT", "UPDATE", "DELETE"})
    private String sqlType;

    @Schema(description = "平均耗时下限（毫秒），只返回窗口平均耗时 ≥ 该值的指纹")
    @Min(value = 0, message = "minAvgMs 不能为负")
    private Long minAvgMs;

    @Schema(description = "平均耗时上限（毫秒），只返回窗口平均耗时 ≤ 该值的指纹")
    @Min(value = 0, message = "maxAvgMs 不能为负")
    private Long maxAvgMs;

    @Schema(description = "排序字段：totalTimerWait（默认）/ execCount / avgTimerWaitUs / "
            + "maxAvgTimerWaitUs / rowsExamined / lastSeen")
    private String sortField;

    @Schema(description = "是否升序（默认 false，即降序）")
    private Boolean asc;

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小（1-100）", example = "20")
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private Integer pageSize = 20;
}
