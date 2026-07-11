package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 慢 SQL 指纹聚类结果（§15.4.6）：把窗口内的慢 SQL 指纹按"语句类型 + 涉及表集合"
 * 聚成簇，回答"慢的其实是同一类查询吗、集中打在哪几张表上"。
 */
@Data
@Schema(description = "慢SQL指纹聚类簇")
public class SlowSqlClusterVo {

    @Schema(description = "簇标识：语句类型 + 表集合（如 SELECT@orders,order_item）")
    private String clusterKey;

    @Schema(description = "语句类型：SELECT / INSERT / UPDATE / DELETE / OTHER")
    private String statementType;

    @Schema(description = "涉及表（去库名前缀，字典序）")
    private List<String> tables;

    @Schema(description = "簇内指纹数")
    private Integer digestCount;

    @Schema(description = "簇内样本执行次数合计")
    private Long sampleCount;

    @Schema(description = "簇内总耗时（毫秒）")
    private Double totalTimeMs;

    @Schema(description = "簇内平均单次耗时（毫秒）")
    private Double avgTimeMs;

    @Schema(description = "簇内最慢单次耗时（毫秒）")
    private Double maxTimeMs;

    @Schema(description = "代表 SQL（簇内耗时占比最大指纹的最新样本，截断展示）")
    private String sampleSql;

    @Schema(description = "主要库名")
    private String schemaName;

    @Schema(description = "簇内 Top 指纹明细（按总耗时降序，最多 5 条）")
    private List<ClusterDigest> digests;

    @Data
    @Schema(description = "簇内指纹明细")
    public static class ClusterDigest {

        @Schema(description = "SQL 指纹")
        private String digest;

        @Schema(description = "样本次数")
        private Long sampleCount;

        @Schema(description = "总耗时（毫秒）")
        private Double totalTimeMs;

        @Schema(description = "平均耗时（毫秒）")
        private Double avgTimeMs;
    }
}
