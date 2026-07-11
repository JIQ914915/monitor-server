package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 首页全局总览（§11.1.2）：整体健康门面 + 5 张状态统计卡 + 数据库类型分布 + 高风险实例 Top10。
 *
 * <p>数据范围为当前用户数据权限内的实例集合（§11.1.5），
 * 不做跨实例 QPS/连接数等无业务意义的简单加总（§11.1.4）。
 */
@Data
@Schema(description = "首页全局总览")
public class FleetOverviewVo {

    // ── 状态统计卡（5 张，normal + alert + abnormal + paused = total）──────────

    @Schema(description = "实例总数", example = "36")
    private int total;

    @Schema(description = "正常实例数（采集正常且无活跃告警）", example = "30")
    private int normal;

    @Schema(description = "告警实例数（存在未恢复告警事件）", example = "3")
    private int alert;

    @Schema(description = "异常实例数（连接失败）", example = "2")
    private int abnormal;

    @Schema(description = "离线实例数（暂停采集）", example = "1")
    private int paused;

    // ── 整体健康门面 ─────────────────────────────────────────────────────────

    @Schema(description = "聚合健康分（有健康分实例的平均值，0-100；-1 表示暂无数据）", example = "86")
    private int avgHealth;

    @Schema(description = "聚合健康等级（字典 health_level）：excellent/good/warning/critical/no_data", example = "good")
    private String healthLevel;

    @Schema(description = "参与健康评估的实例数", example = "33")
    private int scoredCount;

    @Schema(description = "五维构成（各维度达标率 = 该维度全实例平均得分）")
    private List<DimRate> dims;

    @Schema(description = "近 7 天状态趋势（迷你趋势线用，按天一个点、最早在前）")
    private Trends trends;

    // ── 数据库类型分布 ────────────────────────────────────────────────────────

    @Schema(description = "数据库类型分布（总数/正常数/告警数）")
    private List<TypeDist> dbTypes;

    // ── 高风险实例 Top10 ─────────────────────────────────────────────────────

    @Schema(description = "高风险实例 Top10（健康分 <80 或有活跃告警，按健康分从低到高）")
    private List<RiskInstance> topRisk;

    @Data
    @Schema(description = "近 7 天状态趋势（total/alert 基于建档时间与告警事件回溯；abnormal/paused 无历史快照，为当前值持平线）")
    public static class Trends {
        @Schema(description = "实例总数（按实例建档时间回溯）")
        private List<Integer> total;
        @Schema(description = "正常实例数（total - 当日告警实例数的近似值，末位为当前真实值）")
        private List<Integer> normal;
        @Schema(description = "告警实例数（当日触发过告警事件的去重实例数，末位为当前活跃告警实例数）")
        private List<Integer> alert;
        @Schema(description = "异常实例数（无历史快照，当前值持平）")
        private List<Integer> abnormal;
        @Schema(description = "离线实例数（无历史快照，当前值持平）")
        private List<Integer> paused;
    }

    @Data
    @Schema(description = "健康维度达标率")
    public static class DimRate {
        @Schema(description = "维度编码：availability/performance/stability/capacity/security", example = "availability")
        private String key;
        @Schema(description = "维度中文名", example = "可用性")
        private String label;
        @Schema(description = "达标率（0-100，全实例该维度平均得分）；-1 表示暂无数据", example = "96")
        private int rate;
    }

    @Data
    @Schema(description = "数据库类型分布项")
    public static class TypeDist {
        @Schema(description = "数据库类型展示名", example = "MySQL")
        private String name;
        @Schema(description = "该类型实例总数", example = "20")
        private int total;
        @Schema(description = "正常实例数", example = "18")
        private int normal;
        @Schema(description = "告警实例数（存在活跃告警）", example = "2")
        private int alert;
    }

    @Data
    @Schema(description = "高风险实例条目")
    public static class RiskInstance {
        @Schema(description = "实例 ID")
        private Long id;
        @Schema(description = "实例名称")
        private String name;
        @Schema(description = "数据库类型展示名", example = "MySQL")
        private String dbType;
        @Schema(description = "数据库版本编码", example = "8.0")
        private String dbVersion;
        @Schema(description = "主机")
        private String host;
        @Schema(description = "端口")
        private Integer port;
        @Schema(description = "所属分组名称（多分组）")
        private List<String> groupNames;
        @Schema(description = "负责人A姓名")
        private String ownerAName;
        @Schema(description = "负责人B姓名（可选）")
        private String ownerBName;
        @Schema(description = "实例状态（字典 instance_status）：normal/abnormal/paused")
        private String status;
        @Schema(description = "健康分（0-100）；null 表示暂无评分", example = "62")
        private Integer health;
        @Schema(description = "健康等级（字典 health_level）", example = "warning")
        private String healthLevel;
        @Schema(description = "活跃告警数（未恢复事件）", example = "2")
        private int activeAlerts;
    }
}
