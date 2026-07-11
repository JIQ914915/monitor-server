package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 实例容量增长趋势 VO（7 日环比，最近 N 天）。 */
@Data
@Schema(description = "实例容量增长趋势（日级快照 + 7 日环比）")
public class CapacityGrowthVo {

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "趋势数据点（按日期升序），空列表表示暂无历史数据（物化视图未刷新）")
    private List<DailyPoint> trend;

    @Data
    @Schema(description = "单日容量数据点")
    public static class DailyPoint {

        @Schema(description = "日期，格式 yyyy-MM-dd")
        private String day;

        @Schema(description = "当日库表总容量（字节）")
        private Long currentBytes;

        @Schema(description = "7 天前容量（字节）；首周无历史数据时为 null")
        private Long prevWeekBytes;

        @Schema(description = "7 日增长字节数（currentBytes - prevWeekBytes）；null 表示无对比基准")
        private Long growthBytes;

        @Schema(description = "7 日增长率（%，保留 2 位小数）；null 表示无对比基准")
        private Double growthRatePct;
    }
}
