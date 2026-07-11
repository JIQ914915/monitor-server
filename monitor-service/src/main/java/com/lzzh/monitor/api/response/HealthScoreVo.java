package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 实例健康评分 VO（实时概况 Banner 健康分 + 雷达图维度）。
 *
 * <p>健康分由五个维度加权合成，每个维度满分 100，
 * 综合得分 = 各维度分 × 权重之和：
 * 可用性(30%) + 性能(25%) + 稳定性(20%) + 容量(15%) + 安全配置(10%)。
 *
 * <p>健康等级：
 * <ul>
 *   <li>90~100：excellent（优）</li>
 *   <li>75~89：good（良）</li>
 *   <li>60~74：warning（警告）</li>
 *   <li>0~59：critical（危险）</li>
 *   <li>-1：无数据（实例尚无采集数据或数据已过期）</li>
 * </ul>
 */
@Data
@Schema(description = "实例健康评分（五维度加权）")
public class HealthScoreVo {

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "综合健康分（0-100）；-1 表示无可用数据", example = "88")
    private int score;

    @Schema(description = "健康等级：excellent / good / warning / critical / no_data", example = "good")
    private String level;

    @Schema(description = "五维度得分列表（各维度满分 100，可绘制雷达图）")
    private List<DimensionScore> dimensions;

    @Schema(description = "扣分明细（score < 100 时，列出具体扣分原因）")
    private List<Deduction> deductions;

    @Data
    @Schema(description = "维度得分")
    public static class DimensionScore {

        @Schema(description = "维度编码：availability / performance / stability / capacity / security",
                example = "availability")
        private String dimension;

        @Schema(description = "维度中文名", example = "可用性")
        private String label;

        @Schema(description = "维度得分（0-100）；-1 表示无数据", example = "100")
        private int score;

        @Schema(description = "该维度的权重（%）", example = "30")
        private int weight;
    }

    @Data
    @Schema(description = "单条扣分明细")
    public static class Deduction {

        @Schema(description = "所属维度", example = "performance")
        private String dimension;

        @Schema(description = "描述", example = "连接使用率 ≥ 90%（当前值：93.5%）")
        private String message;

        @Schema(description = "扣分分值", example = "15")
        private int points;

        @Schema(description = "当前指标值（字符串，便于含单位展示）", example = "93.5%")
        private String currentValue;
    }
}
