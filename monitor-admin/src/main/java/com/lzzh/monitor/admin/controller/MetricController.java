package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.CapacityGrowthRequest;
import com.lzzh.monitor.api.request.HostDiskIoTrendRequest;
import com.lzzh.monitor.api.request.MetricInstanceRequest;
import com.lzzh.monitor.api.request.MetricLatestRequest;
import com.lzzh.monitor.api.request.MetricObjectRequest;
import com.lzzh.monitor.api.request.MetricTextHistoryRequest;
import com.lzzh.monitor.api.request.MetricTextLatestRequest;
import com.lzzh.monitor.api.request.MetricTrendRequest;
import com.lzzh.monitor.api.request.ParamMetaRequest;
import com.lzzh.monitor.api.request.PerfTrendBatchRequest;
import com.lzzh.monitor.api.request.ParamPageRequest;
import com.lzzh.monitor.api.request.TableGrowthRequest;
import com.lzzh.monitor.api.request.TableIoPageRequest;
import com.lzzh.monitor.api.request.UnusedIndexPageRequest;
import com.lzzh.monitor.api.response.CapacityForecastVo;
import com.lzzh.monitor.api.response.CapacityGrowthVo;
import com.lzzh.monitor.api.response.HealthScoreVo;
import com.lzzh.monitor.api.response.HostDiskIoTrendVo;
import com.lzzh.monitor.api.response.LongConnVo;
import com.lzzh.monitor.api.response.MetricLatestVo;
import com.lzzh.monitor.api.response.MetricObjectVo;
import com.lzzh.monitor.api.response.MetricTextVo;
import com.lzzh.monitor.api.response.MetricTrendVo;
import com.lzzh.monitor.api.response.ParamAdviceVo;
import com.lzzh.monitor.api.response.ParamCurrentVo;
import com.lzzh.monitor.api.response.ParamMetaVo;
import com.lzzh.monitor.api.response.PerfTrendBatchVo;
import com.lzzh.monitor.api.response.ParamPageItemVo;
import com.lzzh.monitor.api.response.TableGrowthVo;
import com.lzzh.monitor.api.response.TableIoPageVo;
import com.lzzh.monitor.api.response.TodayStatsVo;
import com.lzzh.monitor.api.response.UnusedIndexPageVo;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.metric.HealthScoreService;
import com.lzzh.monitor.service.metric.MetricQueryService;
import com.lzzh.monitor.service.metric.ParamAdviceService;
import com.lzzh.monitor.service.metric.ParamMetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 指标数据查询 API（时序数据，非元数据管理）。
 * <p>区别于 {@link MetricDefinitionController}（管理指标定义元数据），
 * 本控制器负责查询实际采集的时序数据，用于实时概况、趋势图等。
 * <p>全部端点使用 POST + JSON 请求体（统一规范，不使用 GET + QueryParam）。
 */
@Tag(name = "指标数据查询", description = "实时概况 / 趋势图所需的时序指标数据查询")
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {

    private final MetricQueryService metricQueryService;
    private final HealthScoreService healthScoreService;
    private final ParamMetaService paramMetaService;
    private final ParamAdviceService paramAdviceService;

    public MetricController(MetricQueryService metricQueryService,
                            HealthScoreService healthScoreService,
                            ParamMetaService paramMetaService,
                            ParamAdviceService paramAdviceService) {
        this.metricQueryService = metricQueryService;
        this.healthScoreService = healthScoreService;
        this.paramMetaService = paramMetaService;
        this.paramAdviceService = paramAdviceService;
    }

    @Operation(
            summary = "实例健康评分",
            description = "基于五维度（可用性30%+性能25%+稳定性20%+容量15%+安全10%）实时计算健康分（0-100）及扣分明细"
    )
    @PostMapping("/health-score")
    public Result<HealthScoreVo> healthScore(@Valid @RequestBody MetricInstanceRequest req) {
        return Result.ok(healthScoreService.calculate(req.getInstanceId()));
    }

    @Operation(
            summary = "实例容量增长趋势（7 日环比）",
            description = "查询最近 N 天日级库表总容量快照与 7 日环比增长率，用于容量趋势图"
    )
    @PostMapping("/capacity/growth-trend")
    public Result<CapacityGrowthVo> capacityGrowthTrend(@Valid @RequestBody CapacityGrowthRequest req) {
        return Result.ok(metricQueryService.capacityGrowthTrend(req.getInstanceId(), req.getDays()));
    }

    @Operation(
            summary = "实例容量预测（预计剩余可用天数）",
            description = "基于最近 15 天日级库表容量快照的线性日均增长 + 关联主机数据盘（容量最大挂载点）剩余空间，"
                    + "估算预计剩余可用天数。无法估算时 estimatedDaysRemaining 为 null，note 说明原因"
    )
    @PostMapping("/capacity/forecast")
    public Result<CapacityForecastVo> capacityForecast(@Valid @RequestBody MetricInstanceRequest req) {
        return Result.ok(metricQueryService.capacityForecast(req.getInstanceId()));
    }

    @Operation(
            summary = "单指标趋势查询",
            description = "查询指定实例某指标在时间范围内的趋势数据，最多返回 2000 个点。"
                    + "frequency=1m 查分钟级原始表；frequency=1h 为小时级视图（分钟级指标由连续聚合降采样，"
                    + "容量类小时采集指标直查 1h 原始表）。from/to 不传时默认取最近 1 小时。"
    )
    @PostMapping("/trend")
    public Result<MetricTrendVo> metricTrend(@Valid @RequestBody MetricTrendRequest req) {
        long from = req.getFrom() != null ? req.getFrom() : 0L;
        long to   = req.getTo()   != null ? req.getTo()   : 0L;
        return Result.ok(metricQueryService.metricTrend(
                req.getInstanceId(), req.getMetricCode(), from, to, req.getFrequency()));
    }

    @Operation(
            summary = "性能分析多指标趋势批量查询",
            description = "一次返回多个指标在同一时间范围内的趋势序列（性能分析页分类图表取数）。"
                    + "frequency=1h（默认）为小时级视图：分钟级指标由 1m 连续聚合降采样（保留 180 天），"
                    + "容量类小时采集指标直查 1h 原始表；frequency=1m 查分钟级原始数据（保留 30 天）。"
                    + "from/to 不传时默认取最近 24 小时。"
    )
    @PostMapping("/perf/trend-batch")
    public Result<PerfTrendBatchVo> perfTrendBatch(@Valid @RequestBody PerfTrendBatchRequest req) {
        long from = req.getFrom() != null ? req.getFrom() : 0L;
        long to   = req.getTo()   != null ? req.getTo()   : 0L;
        return Result.ok(metricQueryService.perfTrendBatch(
                req.getInstanceId(), req.getMetricCodes(), from, to, req.getFrequency()));
    }

    @Operation(
            summary = "多指标最新值批量查询",
            description = "批量查询指定实例多个指标的最新值（metric_data_1m，10 分钟新鲜窗口），无新鲜数据时对应值为 null"
    )
    @PostMapping("/latest")
    public Result<MetricLatestVo> latestMetrics(@Valid @RequestBody MetricLatestRequest req) {
        return Result.ok(metricQueryService.latestMetrics(req.getInstanceId(), req.getCodes()));
    }

    @Operation(
            summary = "对象级指标 Top N 查询",
            description = "查询指定对象指标的 Top N 条目（metric_capacity_object，2 小时新鲜窗口），"
                    + "按 value 降序，适用于表空间 Top N、连接来源 Top N 等场景"
    )
    @PostMapping("/objects")
    public Result<MetricObjectVo> metricObjects(@Valid @RequestBody MetricObjectRequest req) {
        return Result.ok(metricQueryService.metricObjects(
                req.getInstanceId(), req.getMetricCode(), req.getLimit()));
    }

    @Operation(
            summary = "表 I/O 热点分页查询",
            description = "查询近 1 小时表级 I/O 热点（metric_capacity_object，2 小时新鲜窗口），"
                    + "按等待耗时降序分页，并合并同表的读写操作次数。MySQL 5.6 无数据"
    )
    @PostMapping("/table-io/page")
    public Result<PageResult<TableIoPageVo>> tableIoPage(@Valid @RequestBody TableIoPageRequest req) {
        return Result.ok(metricQueryService.tableIoPage(
                req.getInstanceId(), req.getPageNum(), req.getPageSize()));
    }

    @Operation(
            summary = "疑似未使用索引分页查询",
            description = "解析天级文本指标 mysql.index.unused_list，按库/表/索引名顺序分页返回；"
                    + "同时返回实例 uptimeDays 供前端提示结论可靠性。删除索引前须人工确认"
    )
    @PostMapping("/unused-index/page")
    public Result<UnusedIndexPageVo> unusedIndexPage(@Valid @RequestBody UnusedIndexPageRequest req) {
        return Result.ok(metricQueryService.unusedIndexPage(
                req.getInstanceId(), req.getPageNum(), req.getPageSize()));
    }

    @Operation(
            summary = "当前长连接明细查询",
            description = "查询实例当前长连接列表（metric_long_conn，10 分钟新鲜窗口），按持续时间降序，最多 500 条"
    )
    @PostMapping("/long-connections")
    public Result<LongConnVo> longConnections(@Valid @RequestBody MetricInstanceRequest req) {
        return Result.ok(metricQueryService.longConnections(req.getInstanceId()));
    }

    @Operation(
            summary = "配置参数当前值查询",
            description = "查询实例 mysql.var.* 数值型参数和 mysql.var_text.* 文本型参数的最新值（天级，2天新鲜窗口），"
                    + "用于实时概况「配置 Tab」当前值列"
    )
    @PostMapping("/params/current")
    public Result<ParamCurrentVo> paramsCurrent(@Valid @RequestBody MetricInstanceRequest req) {
        return Result.ok(metricQueryService.paramsCurrent(req.getInstanceId()));
    }

    @Operation(
            summary = "配置参数元数据查询",
            description = "查询 MySQL 配置参数的静态元数据（描述、分类、动态性、适用版本），用于配置 Tab 说明列"
    )
    @PostMapping("/params/meta")
    public Result<List<ParamMetaVo>> paramsMeta(@RequestBody ParamMetaRequest req) {
        return Result.ok(paramMetaService.listByCategory(req.getCategory()));
    }

    @Operation(
            summary = "参数调优建议",
            description = "基于已采集的配置参数（天级快照）与运行指标（Buffer Pool 命中率/连接使用率/磁盘临时表占比等）"
                    + "做规则化体检，输出观察依据与调整建议。只出建议不出手，任何调整须人工评估后走变更流程执行"
    )
    @PostMapping("/params/advice")
    public Result<List<ParamAdviceVo>> paramsAdvice(@Valid @RequestBody MetricInstanceRequest req) {
        return Result.ok(paramAdviceService.advise(req.getInstanceId()));
    }

    @Operation(
            summary = "配置参数分页查询",
            description = "合并当前值（mysql.var.* / mysql.var_text.*）与参数元数据，支持按关键词（参数名/说明）和分类过滤，服务端分页"
    )
    @PostMapping("/params/page")
    public Result<PageResult<ParamPageItemVo>> paramsPage(@Valid @RequestBody ParamPageRequest req) {
        return Result.ok(metricQueryService.paramsPage(req));
    }

    @Operation(
            summary = "表级周环比增长 Top N",
            description = "基于 metric_capacity_object 小时快照计算每张表的周环比增长，"
                    + "按增长字节数降序，无上周数据的表排最后"
    )
    @PostMapping("/objects/growth")
    public Result<TableGrowthVo> tableGrowth(@Valid @RequestBody TableGrowthRequest req) {
        return Result.ok(metricQueryService.tableGrowth(
                req.getInstanceId(), req.getMetricCode(), req.getLimit()));
    }

    @Operation(
            summary = "今日累计统计",
            description = "查询实例今日创建内存临时表数、磁盘临时表数、慢查询数及磁盘临时表占比，"
                    + "汇总 metric_data_1m 中今日 delta 增量"
    )
    @PostMapping("/today-stats")
    public Result<TodayStatsVo> todayStats(@Valid @RequestBody MetricInstanceRequest req) {
        return Result.ok(metricQueryService.todayStats(req.getInstanceId()));
    }

    @Operation(
            summary = "文本指标最新值批量查询",
            description = "查询文本指标（mysql.var_text.*、复制状态等）的最新值，"
                    + "frequency=1m 取分钟表（30分钟新鲜窗口），frequency=1d 取天表（2天新鲜窗口）"
    )
    @PostMapping("/text/latest")
    public Result<MetricTextVo> latestTextMetrics(@Valid @RequestBody MetricTextLatestRequest req) {
        return Result.ok(metricQueryService.latestTextMetrics(
                req.getInstanceId(), req.getCodes(), req.getFrequency()));
    }

    @Operation(
            summary = "主机磁盘 IO 按盘趋势查询",
            description = "从 host.diskio.detail 分钟级明细历史透视出每块盘（Linux 设备 / Windows 盘符）的"
                    + "IO 繁忙度（%）、读速率、写速率（B/s）三组趋势序列。from/to 不传时默认最近 24 小时"
    )
    @PostMapping("/host/diskio-trend")
    public Result<HostDiskIoTrendVo> hostDiskIoTrend(@Valid @RequestBody HostDiskIoTrendRequest req) {
        long from = req.getFrom() != null ? req.getFrom() : 0L;
        long to   = req.getTo()   != null ? req.getTo()   : 0L;
        return Result.ok(metricQueryService.hostDiskIoTrend(req.getInstanceId(), from, to));
    }

    @Operation(
            summary = "文本指标变更历史查询",
            description = "查询单个文本指标（1d 表）的变更历史，最多 100 条，按时间降序，适用于配置参数变更审计"
    )
    @PostMapping("/text/history")
    public Result<MetricTextVo.HistoryVo> textMetricHistory(@Valid @RequestBody MetricTextHistoryRequest req) {
        return Result.ok(metricQueryService.textMetricHistory(req.getInstanceId(), req.getMetricCode()));
    }
}
