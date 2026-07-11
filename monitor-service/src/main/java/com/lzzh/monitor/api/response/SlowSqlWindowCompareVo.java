package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 慢 SQL 时间窗口对比 VO：当前窗口 vs 昨日同时段 vs 上周同时段。
 * <p>用于慢 SQL 页面的「时段对比卡片」，展示整体量级变化与 Top SQL 排名变化。
 */
@Data
@Schema(description = "慢SQL时段对比（当前窗口 vs 昨日同时段 vs 上周同时段）")
public class SlowSqlWindowCompareVo {

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "当前窗口汇总")
    private WindowSummary current;

    @Schema(description = "昨日同时段汇总（窗口整体前移 24 小时）")
    private WindowSummary yesterday;

    @Schema(description = "上周同时段汇总（窗口整体前移 7 天）")
    private WindowSummary lastWeek;

    @Schema(description = "当前窗口 Top SQL（按总耗时降序）及其在对比窗口中的排名与耗时")
    private List<TopItem> topItems;

    /** 单窗口汇总统计。 */
    @Data
    @Schema(description = "单窗口汇总统计")
    public static class WindowSummary {

        @Schema(description = "窗口起点（毫秒时间戳）")
        private Long from;

        @Schema(description = "窗口终点（毫秒时间戳）")
        private Long to;

        @Schema(description = "窗口内慢SQL指纹数")
        private Long digestCount;

        @Schema(description = "窗口内总执行次数")
        private Long totalExecCount;

        @Schema(description = "窗口内平均执行耗时（ms）")
        private Double avgTimeMs;

        @Schema(description = "窗口内最慢指纹的平均耗时（ms）")
        private Double maxAvgTimeMs;
    }

    /** 当前窗口 Top SQL 条目及跨窗口对比信息。 */
    @Data
    @Schema(description = "Top SQL 条目（当前窗口排名 + 对比窗口排名/耗时）")
    public static class TopItem {

        @Schema(description = "库名")
        private String schemaName;

        @Schema(description = "SQL 指纹")
        private String digest;

        @Schema(description = "归一化 SQL 文本")
        private String digestText;

        @Schema(description = "SQL 类型（SELECT/INSERT/UPDATE/DELETE/OTHER）")
        private String sqlType;

        @Schema(description = "当前窗口排名（按总耗时降序，从 1 开始）")
        private Integer rank;

        @Schema(description = "当前窗口执行次数")
        private Long execCount;

        @Schema(description = "当前窗口平均耗时（ms）")
        private Double avgTimeMs;

        @Schema(description = "昨日同时段排名；null 表示昨日未进入 Top 榜（新上榜或量级极低）")
        private Integer yesterdayRank;

        @Schema(description = "昨日同时段平均耗时（ms）；null 表示昨日无数据")
        private Double yesterdayAvgTimeMs;

        @Schema(description = "上周同时段排名；null 表示上周未进入 Top 榜")
        private Integer lastWeekRank;

        @Schema(description = "上周同时段平均耗时（ms）；null 表示上周无数据")
        private Double lastWeekAvgTimeMs;
    }
}
