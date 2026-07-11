package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.SecurityUtils;
import com.lzzh.monitor.api.request.SlowSqlDigestDetailRequest;
import com.lzzh.monitor.api.request.SlowSqlDigestPageRequest;
import com.lzzh.monitor.api.request.SlowSqlDigestTrendRequest;
import com.lzzh.monitor.api.request.SlowSqlExplainRequest;
import com.lzzh.monitor.api.request.SlowSqlOptimizeMarkRequest;
import com.lzzh.monitor.api.request.SlowSqlRecordPageRequest;
import com.lzzh.monitor.api.request.SlowSqlSamplePageRequest;
import com.lzzh.monitor.api.request.SlowSqlWindowRequest;
import com.lzzh.monitor.api.response.SlowSqlAlertVo;
import com.lzzh.monitor.api.response.SlowSqlDigestTrendVo;
import com.lzzh.monitor.api.response.SlowSqlDigestVo;
import com.lzzh.monitor.api.response.SlowSqlExplainVo;
import com.lzzh.monitor.api.response.SlowSqlRecordVo;
import com.lzzh.monitor.api.response.SlowSqlSampleVo;
import com.lzzh.monitor.api.response.SlowSqlStatsVo;
import com.lzzh.monitor.api.response.SlowSqlWindowCompareVo;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.metric.SlowSqlExplainService;
import com.lzzh.monitor.service.metric.SlowSqlQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 慢 SQL 分析接口。
 * <p>数据源：metric_top_sql（performance_schema digest 小时级周期增量，保留 180 天），
 * 查询侧按 (schema_name, digest) 聚合时间窗口内增量，得到窗口 Top SQL 排名。
 * MySQL 5.6 不支持 digest 采集，对应实例列表为空（stats 中 topSqlSupported=false 提示前端）。
 * <p>全部端点使用 POST + JSON 请求体。
 */
@Tag(name = "慢SQL分析", description = "Top SQL 指纹排名 / 概览统计 / 单指纹趋势")
@RestController
@RequestMapping("/api/v1/slow-sql")
public class SlowSqlController {

    private final SlowSqlQueryService slowSqlQueryService;
    private final SlowSqlExplainService slowSqlExplainService;

    public SlowSqlController(SlowSqlQueryService slowSqlQueryService,
                             SlowSqlExplainService slowSqlExplainService) {
        this.slowSqlQueryService = slowSqlQueryService;
        this.slowSqlExplainService = slowSqlExplainService;
    }

    @Operation(
            summary = "慢SQL指纹聚合分页",
            description = "按 (库名, SQL指纹) 聚合时间窗口内的执行次数/耗时/扫描行数增量，"
                    + "支持 SQL 关键词、库名、SQL 类型、平均耗时下限过滤与多字段排序，服务端分页。"
                    + "from/to 不传默认最近 24 小时"
    )
    @PostMapping("/digest/page")
    public Result<PageResult<SlowSqlDigestVo>> digestPage(@Valid @RequestBody SlowSqlDigestPageRequest req) {
        return Result.ok(slowSqlQueryService.pageDigest(req));
    }

    @Operation(
            summary = "慢SQL采集周期明细分页",
            description = "每行为某 SQL 指纹在某个采集周期（小时级）内的增量记录，按采集时间倒序，"
                    + "支持 SQL 类型与周期平均耗时区间过滤，服务端分页。from/to 不传默认最近 24 小时"
    )
    @PostMapping("/records/page")
    public Result<PageResult<SlowSqlRecordVo>> recordsPage(@Valid @RequestBody SlowSqlRecordPageRequest req) {
        return Result.ok(slowSqlQueryService.pageRecords(req));
    }

    @Operation(
            summary = "单指纹窗口聚合详情",
            description = "返回指定 SQL 指纹在时间窗口内的聚合统计（详情弹窗数据源），窗口内无数据返回 null"
    )
    @PostMapping("/digest/detail")
    public Result<SlowSqlDigestVo> digestDetail(@Valid @RequestBody SlowSqlDigestDetailRequest req) {
        return Result.ok(slowSqlQueryService.digestDetail(req));
    }

    @Operation(
            summary = "慢SQL概览统计",
            description = "返回窗口内指纹数、总执行次数、总耗时、最慢平均耗时、总扫描行数，"
                    + "以及今日慢查询数（Slow_queries 增量合计）和 long_query_time 阈值"
    )
    @PostMapping("/stats")
    public Result<SlowSqlStatsVo> stats(@Valid @RequestBody SlowSqlWindowRequest req) {
        return Result.ok(slowSqlQueryService.stats(req));
    }

    @Operation(
            summary = "单指纹小时级趋势",
            description = "返回指定 SQL 指纹各采集周期的执行次数/平均耗时/扫描行数，"
                    + "供详情弹窗趋势图。from/to 不传默认最近 7 天"
    )
    @PostMapping("/digest/trend")
    public Result<SlowSqlDigestTrendVo> digestTrend(@Valid @RequestBody SlowSqlDigestTrendRequest req) {
        return Result.ok(slowSqlQueryService.digestTrend(req));
    }

    @Operation(
            summary = "慢SQL真实执行样本分页",
            description = "每行为一次真实执行的慢 SQL（含参数原文），数据源 events_statements_history 分钟级采集，"
                    + "保留 7 天。支持 SQL 类型、执行耗时区间、指纹过滤与排序。"
                    + "history 每线程仅保留最近 10 条语句，样本为抽样非全量"
    )
    @PostMapping("/samples/page")
    public Result<PageResult<SlowSqlSampleVo>> samplesPage(@Valid @RequestBody SlowSqlSamplePageRequest req) {
        return Result.ok(slowSqlQueryService.pageSamples(req));
    }

    @Operation(
            summary = "标记指纹优化状态",
            description = "人工标记 SQL 指纹的优化处理状态（字典 slow_sql_optimize_status：unoptimized/optimized），"
                    + "按 (实例, 库, 指纹) upsert"
    )
    @PostMapping("/optimize-status/set")
    public Result<Void> markOptimizeStatus(@Valid @RequestBody SlowSqlOptimizeMarkRequest req) {
        slowSqlQueryService.markOptimizeStatus(req, SecurityUtils.current().username());
        return Result.ok();
    }

    @Operation(
            summary = "慢SQL相关告警事件列表",
            description = "返回窗口内依赖慢查询指标（mysql.delta.slow_queries）的规则触发的告警事件，"
                    + "活跃期与窗口有交叠即返回，按触发时间倒序，最多 100 条。"
                    + "指纹表的「关联告警」由前端按事件活跃期与指纹出现期交叠计算"
    )
    @PostMapping("/alerts")
    public Result<List<SlowSqlAlertVo>> alerts(@Valid @RequestBody SlowSqlWindowRequest req) {
        return Result.ok(slowSqlQueryService.listSlowSqlAlerts(req));
    }

    @Operation(
            summary = "实时执行计划",
            description = "使用实例采集账号连到目标库执行 EXPLAIN（只做优化器分析，不执行语句本身），"
                    + "返回列名+行值透传结果。仅支持 SELECT/INSERT/UPDATE/DELETE/REPLACE 单条完整语句；"
                    + "采集时被目标库截断的 SQL 无法生成执行计划"
    )
    @PostMapping("/explain")
    public Result<SlowSqlExplainVo> explain(@Valid @RequestBody SlowSqlExplainRequest req) {
        return Result.ok(slowSqlExplainService.explain(req));
    }

    @Operation(
            summary = "慢SQL时段对比",
            description = "对比当前窗口与昨日同时段、上周同时段的慢SQL整体量级（指纹数/执行次数/平均耗时），"
                    + "并返回当前窗口 Top 10 SQL（按总耗时降序）在两个对比窗口中的排名与平均耗时变化。"
                    + "from/to 不传默认最近 24 小时。对比窗口未进入 Top 50 的指纹排名返回 null（视为新上榜）"
    )
    @PostMapping("/window-compare")
    public Result<SlowSqlWindowCompareVo> windowCompare(@Valid @RequestBody SlowSqlWindowRequest req) {
        return Result.ok(slowSqlQueryService.windowCompare(req));
    }

    @Operation(
            summary = "窗口内库名列表",
            description = "返回时间窗口内出现过 Top SQL 的库名去重列表，供筛选下拉"
    )
    @PostMapping("/schemas")
    public Result<List<String>> schemas(@Valid @RequestBody SlowSqlWindowRequest req) {
        return Result.ok(slowSqlQueryService.listSchemas(req));
    }

    @Operation(
            summary = "慢SQL指纹聚类（分页）",
            description = "把窗口内慢SQL真实样本按结构相似度（语句类型 + 涉及表集合）聚成簇，"
                    + "回答\"慢的是不是同一类查询、集中打在哪几张表\"。按簇总耗时降序，"
                    + "服务端完成聚簇后按 pageNum/pageSize 分页返回 {list, total}。"
                    + "from/to 不传默认最近 24 小时"
    )
    @PostMapping("/clusters")
    public Result<PageResult<com.lzzh.monitor.api.response.SlowSqlClusterVo>> clusters(
            @Valid @RequestBody com.lzzh.monitor.api.request.SlowSqlClusterPageRequest req) {
        return Result.ok(slowSqlQueryService.clusters(req));
    }
}
