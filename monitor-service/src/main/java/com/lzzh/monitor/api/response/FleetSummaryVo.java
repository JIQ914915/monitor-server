package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 实例舰队概况（仪表盘用）。 */
@Data
@Schema(description = "实例舰队概况")
public class FleetSummaryVo {

    @Schema(description = "实例总数", example = "36")
    private int total;

    @Schema(description = "正常（采集中）实例数", example = "33")
    private int normal;

    @Schema(description = "异常（连接失败）实例数", example = "2")
    private int abnormal;

    @Schema(description = "暂停采集实例数", example = "1")
    private int paused;

    @Schema(description = "全部实例平均健康度（0-100）", example = "88")
    private int avgHealth;

    @Schema(description = "健康等级分布")
    private List<LevelCount> dist;

    @Data
    @Schema(description = "健康等级分布项")
    public static class LevelCount {
        @Schema(description = "健康等级：excellent/good/warning/critical/offline", example = "good")
        private String level;
        @Schema(description = "该等级实例数", example = "15")
        private int count;
    }
}
