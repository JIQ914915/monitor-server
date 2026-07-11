package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 慢 SQL 采集周期明细分页查询入参。
 * <p>每行为某 digest 在某个采集周期（小时级）内的增量记录，按采集时间倒序，
 * 用于慢SQL列表（区别于按指纹聚合的 SQL 指纹分析表）。
 */
@Data
@Schema(description = "慢SQL采集周期明细分页查询入参")
public class SlowSqlRecordPageRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "开始时间（毫秒时间戳，含）；不传默认为 now-24h", example = "1751500000000")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳，含）；不传默认为 now", example = "1751586400000")
    private Long to;

    @Schema(description = "SQL 类型过滤：SELECT / INSERT / UPDATE / DELETE，不传为全部",
            allowableValues = {"SELECT", "INSERT", "UPDATE", "DELETE"})
    private String sqlType;

    @Schema(description = "SQL 指纹过滤（指纹详情的 SQL 明细列表使用）；传入时 schemaName 同时参与精确匹配")
    private String digest;

    @Schema(description = "库名（仅与 digest 联用，可空表示无库上下文）")
    private String schemaName;

    @Schema(description = "周期平均耗时下限（毫秒）")
    @Min(value = 0, message = "minAvgMs 不能为负")
    private Long minAvgMs;

    @Schema(description = "周期平均耗时上限（毫秒）")
    @Min(value = 0, message = "maxAvgMs 不能为负")
    private Long maxAvgMs;

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小（1-100）", example = "20")
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private Integer pageSize = 20;
}
