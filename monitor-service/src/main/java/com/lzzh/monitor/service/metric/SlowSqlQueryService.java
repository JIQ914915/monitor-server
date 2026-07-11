package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.request.SlowSqlDigestDetailRequest;
import com.lzzh.monitor.api.request.SlowSqlDigestPageRequest;
import com.lzzh.monitor.api.request.SlowSqlDigestTrendRequest;
import com.lzzh.monitor.api.request.SlowSqlOptimizeMarkRequest;
import com.lzzh.monitor.api.request.SlowSqlRecordPageRequest;
import com.lzzh.monitor.api.request.SlowSqlSamplePageRequest;
import com.lzzh.monitor.api.request.SlowSqlWindowRequest;
import com.lzzh.monitor.api.response.SlowSqlAlertVo;
import com.lzzh.monitor.api.response.SlowSqlDigestTrendVo;
import com.lzzh.monitor.api.response.SlowSqlDigestVo;
import com.lzzh.monitor.api.response.SlowSqlRecordVo;
import com.lzzh.monitor.api.response.SlowSqlSampleVo;
import com.lzzh.monitor.api.response.SlowSqlStatsVo;
import com.lzzh.monitor.api.response.SlowSqlWindowCompareVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/**
 * 慢 SQL 分析查询服务。
 * <p>数据源为 metric_top_sql（小时级 digest 周期增量，保留 180 天），
 * 查询侧按 (schema_name, digest) 聚合时间窗口内增量得到窗口 Top SQL 排名。
 */
public interface SlowSqlQueryService {

    /** 指纹聚合分页列表（SQL 指纹分析表）。 */
    PageResult<SlowSqlDigestVo> pageDigest(SlowSqlDigestPageRequest request);

    /** 采集周期明细分页列表（慢SQL列表，每行 = 某 digest 某采集周期的增量）。 */
    PageResult<SlowSqlRecordVo> pageRecords(SlowSqlRecordPageRequest request);

    /** 单指纹时间窗口聚合详情；窗口内无数据返回 null。 */
    SlowSqlDigestVo digestDetail(SlowSqlDigestDetailRequest request);

    /** 慢 SQL 真实执行样本分页（一行 = 一次真实执行，SQL 含参数原文）。 */
    PageResult<SlowSqlSampleVo> pageSamples(SlowSqlSamplePageRequest request);

    /** 标记 SQL 指纹优化状态（upsert）。 */
    void markOptimizeStatus(SlowSqlOptimizeMarkRequest request, String operator);

    /** 窗口内慢 SQL 相关告警事件列表（依赖慢查询指标的规则触发，按触发时间倒序）。 */
    List<SlowSqlAlertVo> listSlowSqlAlerts(SlowSqlWindowRequest request);

    /** 概览统计（窗口汇总 + 今日慢查询数 + long_query_time 阈值）。 */
    SlowSqlStatsVo stats(SlowSqlWindowRequest request);

    /** 单指纹小时级趋势（详情弹窗趋势图）。 */
    SlowSqlDigestTrendVo digestTrend(SlowSqlDigestTrendRequest request);

    /** 窗口内出现过的库名列表（筛选下拉）。 */
    List<String> listSchemas(SlowSqlWindowRequest request);

    /** 时段对比：当前窗口 vs 昨日同时段 vs 上周同时段（汇总 + Top SQL 排名变化）。 */
    SlowSqlWindowCompareVo windowCompare(SlowSqlWindowRequest request);

    /**
     * 指纹聚类（分页）：窗口内慢 SQL 按"语句类型 + 涉及表集合"聚簇，按簇总耗时降序，
     * 服务端完成聚簇后按页返回（{@code {list, total}}）。
     */
    PageResult<com.lzzh.monitor.api.response.SlowSqlClusterVo> clusters(
            com.lzzh.monitor.api.request.SlowSqlClusterPageRequest request);
}
