package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 单个 SQL 指纹的小时级趋势 VO（详情弹窗趋势图）。 */
@Data
@Schema(description = "单指纹小时级趋势")
public class SlowSqlDigestTrendVo {

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "SQL 指纹")
    private String digest;

    @Schema(description = "趋势点列表（按采集时间升序）")
    private List<Point> points;

    /** 单个采集周期的增量统计点。 */
    @Data
    @Schema(description = "指纹趋势点")
    public static class Point {

        @Schema(description = "采集时间（毫秒时间戳）")
        private Long ts;

        @Schema(description = "该周期执行次数")
        private Long execCount;

        @Schema(description = "该周期平均单次耗时（毫秒）")
        private Double avgTimeMs;

        @Schema(description = "该周期扫描行数")
        private Long rowsExamined;
    }
}
