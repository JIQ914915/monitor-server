package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 慢 SQL 真实执行样本分页查询入参。
 * <p>数据源 metric_slow_sql_sample（events_statements_history 分钟级采集，保留 7 天），
 * 每行为一次真实执行（含参数的 SQL 原文）。
 */
@Data
@Schema(description = "慢SQL真实执行样本分页查询入参")
public class SlowSqlSamplePageRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "开始时间（毫秒时间戳，含）；不传默认为 now-24h")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳，含）；不传默认为 now")
    private Long to;

    @Schema(description = "SQL 类型过滤：SELECT / INSERT / UPDATE / DELETE，不传为全部",
            allowableValues = {"SELECT", "INSERT", "UPDATE", "DELETE"})
    private String sqlType;

    @Schema(description = "单次执行耗时下限（毫秒）")
    @Min(value = 0, message = "minExecMs 不能为负")
    private Long minExecMs;

    @Schema(description = "单次执行耗时上限（毫秒）")
    @Min(value = 0, message = "maxExecMs 不能为负")
    private Long maxExecMs;

    @Schema(description = "SQL 指纹过滤（指纹详情的 SQL 明细列表使用）")
    private String digest;

    @Schema(description = "排序字段：execTimeUs / rowsExamined / sortRows / collectTime，默认 execTimeUs")
    private String sortField;

    @Schema(description = "是否升序，默认 false（降序）")
    private Boolean asc;

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小（1-100）", example = "20")
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private Integer pageSize = 20;
}
