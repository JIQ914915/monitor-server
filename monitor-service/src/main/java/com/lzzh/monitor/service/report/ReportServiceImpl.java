package com.lzzh.monitor.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.ReportGenerateRequest;
import com.lzzh.monitor.api.request.ReportPageRequest;
import com.lzzh.monitor.api.request.ReportScheduleSaveRequest;
import com.lzzh.monitor.api.response.DrilldownProfileVo;
import com.lzzh.monitor.api.response.ReportDetailVo;
import com.lzzh.monitor.api.response.ReportScheduleVo;
import com.lzzh.monitor.api.response.ReportVo;
import com.lzzh.monitor.api.response.MetricTrendVo;
import com.lzzh.monitor.common.constant.Constants;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertEventOperateLog;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.InstanceGroup;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.dao.entity.MonitorReport;
import com.lzzh.monitor.dao.entity.ReportSchedule;
import com.lzzh.monitor.dao.entity.SysDictItem;
import com.lzzh.monitor.dao.entity.SysUser;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertEventOperateLogMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleMapper;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.mapper.CollectLogMapper;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.InstanceGroupMapper;
import com.lzzh.monitor.dao.mapper.MetricDefinitionMapper;
import com.lzzh.monitor.dao.mapper.MonitorReportMapper;
import com.lzzh.monitor.dao.mapper.ReportScheduleMapper;
import com.lzzh.monitor.dao.mapper.ReportStatsMapper;
import com.lzzh.monitor.dao.mapper.SysDictItemMapper;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.dao.mapper.TsTopSqlQueryMapper;
import com.lzzh.monitor.dao.ts.TsCapacityGrowthDao;
import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import com.lzzh.monitor.service.alert.AlertDrilldownProfileService;
import com.lzzh.monitor.service.datascope.CurrentUserHolder;
import com.lzzh.monitor.service.datascope.DataScope;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.metric.MetricQueryService;
import com.lzzh.monitor.service.support.Pages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 报告中心实现（§11.9）。
 * <p>报告内容统一为分段结构 {@code {"sections":[{title,type,summary,kv,columns,rows,items}]}}：
 * type=summary（结论 + 键值对）/ table（列定义 + 数据行）/ list（建议条目），
 * 前端预览页按 type 分段渲染，导出 Word 时按同一结构组装 HTML。
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 性能/巡检报告统计的核心指标（编码 → [展示名, 单位符号]）。 */
    private static final LinkedHashMap<String, String[]> CORE_METRICS = new LinkedHashMap<>();
    static {
        CORE_METRICS.put("mysql.qps", new String[]{"QPS", ""});
        CORE_METRICS.put("mysql.tps", new String[]{"TPS", ""});
        CORE_METRICS.put("mysql.conn.total", new String[]{"当前连接数", ""});
        CORE_METRICS.put("mysql.conn.usage", new String[]{"连接使用率", "%"});
        CORE_METRICS.put("mysql.conn.active", new String[]{"活跃连接数", ""});
        CORE_METRICS.put("mysql.delta.slow_queries", new String[]{"慢查询数/分钟", ""});
        CORE_METRICS.put("mysql.perf.avg_stmt_latency_ms", new String[]{"平均响应时间", "ms"});
        CORE_METRICS.put("mysql.innodb.buffer_pool_hit_rate", new String[]{"Buffer Pool 命中率", "%"});
    }

    /** PostgreSQL 核心指标（一期指标集内的对应口径，语句延迟/慢SQL 待二期 pg_stat_statements 接入）。 */
    private static final LinkedHashMap<String, String[]> PG_CORE_METRICS = new LinkedHashMap<>();
    static {
        PG_CORE_METRICS.put("pg.tps", new String[]{"TPS", ""});
        PG_CORE_METRICS.put("pg.conn.total", new String[]{"当前连接数", ""});
        PG_CORE_METRICS.put("pg.conn.usage", new String[]{"连接使用率", "%"});
        PG_CORE_METRICS.put("pg.conn.active", new String[]{"活跃连接数", ""});
        PG_CORE_METRICS.put("pg.cache.hit_rate", new String[]{"缓存命中率", "%"});
        PG_CORE_METRICS.put("pg.delta.temp_files", new String[]{"临时文件数/分钟", ""});
        PG_CORE_METRICS.put("pg.delta.deadlocks", new String[]{"死锁次数/分钟", ""});
        PG_CORE_METRICS.put("pg.locks.waiting", new String[]{"等待中锁请求", ""});
    }

    private final MonitorReportMapper reportMapper;
    private final ReportScheduleMapper scheduleMapper;
    private final ReportStatsMapper statsMapper;
    private final DbInstanceMapper instanceMapper;
    private final InstanceGroupMapper groupMapper;
    private final SysUserMapper userMapper;
    private final SysDictItemMapper dictItemMapper;
    private final TsTopSqlQueryMapper topSqlMapper;
    private final TsMetricLatestDao metricLatestDao;
    private final TsCapacityGrowthDao capacityGrowthDao;
    private final DataScopeService dataScopeService;
    private final AlertDrilldownProfileService drilldownProfileService;
    private final AlertEventMapper alertEventMapper;
    private final AlertEventOperateLogMapper operateLogMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final MetricDefinitionMapper metricDefinitionMapper;
    private final CollectLogMapper collectLogMapper;
    private final MetricQueryService metricQueryService;
    private final ReportMailService reportMailService;
    private final DatabaseTypeMapper databaseTypeMapper;

    /** dbTypeId → 是否 PostgreSQL 的缓存（database_type 行数极少且编码不变更）。 */
    private final Map<Long, Boolean> pgTypeCache = new ConcurrentHashMap<>();

    public ReportServiceImpl(MonitorReportMapper reportMapper,
                             ReportScheduleMapper scheduleMapper,
                             ReportStatsMapper statsMapper,
                             DbInstanceMapper instanceMapper,
                             InstanceGroupMapper groupMapper,
                             SysUserMapper userMapper,
                             SysDictItemMapper dictItemMapper,
                             TsTopSqlQueryMapper topSqlMapper,
                             TsMetricLatestDao metricLatestDao,
                             TsCapacityGrowthDao capacityGrowthDao,
                             DataScopeService dataScopeService,
                             AlertDrilldownProfileService drilldownProfileService,
                             AlertEventMapper alertEventMapper,
                             AlertEventOperateLogMapper operateLogMapper,
                             AlertRuleMapper alertRuleMapper,
                             MetricDefinitionMapper metricDefinitionMapper,
                             CollectLogMapper collectLogMapper,
                             MetricQueryService metricQueryService,
                             ReportMailService reportMailService,
                             DatabaseTypeMapper databaseTypeMapper) {
        this.reportMapper = reportMapper;
        this.scheduleMapper = scheduleMapper;
        this.statsMapper = statsMapper;
        this.instanceMapper = instanceMapper;
        this.groupMapper = groupMapper;
        this.userMapper = userMapper;
        this.dictItemMapper = dictItemMapper;
        this.topSqlMapper = topSqlMapper;
        this.metricLatestDao = metricLatestDao;
        this.capacityGrowthDao = capacityGrowthDao;
        this.dataScopeService = dataScopeService;
        this.drilldownProfileService = drilldownProfileService;
        this.alertEventMapper = alertEventMapper;
        this.operateLogMapper = operateLogMapper;
        this.alertRuleMapper = alertRuleMapper;
        this.metricDefinitionMapper = metricDefinitionMapper;
        this.collectLogMapper = collectLogMapper;
        this.metricQueryService = metricQueryService;
        this.reportMailService = reportMailService;
        this.databaseTypeMapper = databaseTypeMapper;
    }

    /** 实例是否为 PostgreSQL（决定报告段落使用 pg.* 还是 mysql.* 指标口径）。 */
    private boolean isPostgres(DbInstance ins) {
        if (ins == null || ins.getDbTypeId() == null) {
            return false;
        }
        return pgTypeCache.computeIfAbsent(ins.getDbTypeId(), id -> {
            DatabaseType t = databaseTypeMapper.selectById(id);
            return t != null && "POSTGRESQL".equalsIgnoreCase(t.getCode());
        });
    }

    // ── 归档查询 ─────────────────────────────────────────────────────────────

    @Override
    public PageResult<ReportVo> page(ReportPageRequest req) {
        Page<MonitorReport> page = Pages.build(req);
        LambdaQueryWrapper<MonitorReport> qw = new LambdaQueryWrapper<>();
        if (req != null) {
            if (StringUtils.hasText(req.getKeyword())) {
                qw.like(MonitorReport::getTitle, req.getKeyword());
            }
            if (StringUtils.hasText(req.getReportType())) {
                qw.eq(MonitorReport::getReportType, req.getReportType());
            }
        }
        qw.orderByDesc(MonitorReport::getGenerateTime).orderByDesc(MonitorReport::getId);
        return Pages.toResult(reportMapper.selectPage(page, qw)).map(e -> toVo(e, new ReportVo()));
    }

    @Override
    public ReportDetailVo detail(Long id) {
        MonitorReport r = reportMapper.selectById(id);
        if (r == null) {
            throw new BusinessException("报告不存在: " + id);
        }
        ReportDetailVo vo = (ReportDetailVo) toVo(r, new ReportDetailVo());
        vo.setContent(r.getContent());
        return vo;
    }

    private ReportVo toVo(MonitorReport e, ReportVo vo) {
        vo.setId(e.getId());
        vo.setReportCode(e.getReportCode());
        vo.setTitle(e.getTitle());
        vo.setReportType(e.getReportType());
        vo.setCycle(e.getCycle());
        vo.setScopeType(e.getScopeType());
        vo.setScopeText(e.getScopeText());
        vo.setInstanceIds(e.getInstanceIds());
        vo.setTimeRange(e.getTimeRange());
        vo.setGenMode(e.getGenMode());
        vo.setStatus(e.getStatus());
        vo.setCreatedBy(e.getCreatedBy());
        vo.setGenerateTime(fmt(e.getGenerateTime()));
        return vo;
    }

    // ── 生成 ────────────────────────────────────────────────────────────────

    @Override
    public Long generate(ReportGenerateRequest req) {
        if ("event".equals(req.getReportType())) {
            return generateEventReport(req.getEventId());
        }
        ResolvedScope scope = resolveScope(req.getScopeType(), req.getInstanceIds(), req.getGroupIds(), req.getOwnerIds());
        // 手动生成路径校验数据范围（定时任务为系统调用，范围在创建任务时已确定）
        DataScope ds = dataScopeService.currentScope();
        for (Long id : scope.instanceIds()) {
            if (!ds.allows(id)) {
                throw new BusinessException("无权访问实例: " + id);
            }
        }
        MonitorReport report = doGenerate(req.getReportType(), req.getCycle(), scope,
                normalizeRange(req.getTimeRange()), "manual", currentUserName());
        return report.getId();
    }

    /** 生成报告并归档落库（手动与定时共用）。 */
    private MonitorReport doGenerate(String reportType, String cycle, ResolvedScope scope,
                                     String timeRange, String genMode, String createdBy) {
        if (scope.instanceIds().isEmpty()) {
            throw new BusinessException("所选范围内没有可用实例");
        }
        List<DbInstance> instances = instanceMapper.selectByIds(scope.instanceIds()).stream()
                .sorted(Comparator.comparing(DbInstance::getId))
                .toList();
        if (instances.isEmpty()) {
            throw new BusinessException("所选范围内没有可用实例");
        }
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = switch (timeRange) {
            case "7d" -> to.minusDays(7);
            case "30d" -> to.minusDays(30);
            default -> to.minusHours(24);
        };

        String prefix;
        String title;
        List<Map<String, Object>> sections;
        switch (reportType) {
            case "inspection" -> {
                prefix = "INSP";
                title = "数据库巡检报告" + (StringUtils.hasText(cycle) ? "（" + dictLabel("report_cycle", cycle) + "）" : "");
                sections = buildInspectionSections(instances, from, to);
            }
            case "performance" -> {
                prefix = "PERF";
                DbInstance first = instances.get(0);
                title = "性能分析报告 - " + first.getName();
                sections = buildPerformanceSections(first, from, to);
            }
            case "alert" -> {
                prefix = "ALERT";
                title = "告警分析报告";
                sections = buildAlertSections(instances, from, to);
            }
            case "security" -> {
                prefix = "SEC";
                title = "安全专项报告";
                sections = buildSecuritySections(instances, from, to);
            }
            default -> throw new BusinessException("不支持的报告类型: " + reportType);
        }

        MonitorReport report = new MonitorReport();
        report.setReportCode(prefix + "-" + System.currentTimeMillis());
        report.setTitle(title);
        report.setReportType(reportType);
        report.setCycle("inspection".equals(reportType) ? cycle : null);
        report.setScopeType(scope.scopeType());
        report.setScopeText(scope.scopeText());
        report.setInstanceIds(instances.stream().map(DbInstance::getId).toList());
        report.setTimeRange(timeRange);
        report.setGenMode(genMode);
        report.setStatus("archived");
        report.setContent(Map.of("sections", sections));
        report.setCreatedBy(createdBy);
        report.setGenerateTime(OffsetDateTime.now());
        reportMapper.insert(report);
        return report;
    }

    @Override
    public void delete(Long id) {
        reportMapper.deleteById(id);
    }

    // ── 范围解析 ─────────────────────────────────────────────────────────────

    private record ResolvedScope(String scopeType, String scopeText, List<Long> instanceIds) {
    }

    private ResolvedScope resolveScope(String scopeType, List<Long> instanceIds, List<Long> groupIds, List<Long> ownerIds) {
        String type = StringUtils.hasText(scopeType) ? scopeType : "instance";
        switch (type) {
            case "group" -> {
                if (CollectionUtils.isEmpty(groupIds)) {
                    throw new BusinessException("请至少选择一个分组");
                }
                List<Long> ids = instanceMapper.selectIdsByGroupIds(groupIds);
                String names = groupMapper.selectByIds(groupIds).stream()
                        .map(InstanceGroup::getName).collect(Collectors.joining("、"));
                return new ResolvedScope(type, "分组：" + names, ids);
            }
            case "owner" -> {
                if (CollectionUtils.isEmpty(ownerIds)) {
                    throw new BusinessException("请至少选择一个负责人");
                }
                List<Long> ids = instanceMapper.selectList(new LambdaQueryWrapper<DbInstance>()
                                .in(DbInstance::getOwnerAId, ownerIds)
                                .or(w -> w.in(DbInstance::getOwnerBId, ownerIds)))
                        .stream().map(DbInstance::getId).distinct().toList();
                String names = userMapper.selectByIds(ownerIds).stream()
                        .map(u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername())
                        .collect(Collectors.joining("、"));
                return new ResolvedScope(type, "负责人：" + names, ids);
            }
            default -> {
                if (CollectionUtils.isEmpty(instanceIds)) {
                    throw new BusinessException("请至少选择一个实例");
                }
                List<DbInstance> list = instanceMapper.selectByIds(instanceIds);
                String names = list.stream().map(DbInstance::getName).collect(Collectors.joining("、"));
                return new ResolvedScope("instance",
                        list.size() <= 3 ? "实例：" + names : "实例：" + list.size() + " 个",
                        list.stream().map(DbInstance::getId).toList());
            }
        }
    }

    // ── 巡检报告 ─────────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildInspectionSections(List<DbInstance> instances,
                                                              OffsetDateTime from, OffsetDateTime to) {
        List<Map<String, Object>> sections = new ArrayList<>();
        List<Long> ids = instances.stream().map(DbInstance::getId).toList();
        Map<Long, Integer> activeAlerts = countActiveAlerts(ids);

        // 1. 巡检概况
        int healthy = 0, warning = 0, critical = 0, noData = 0;
        for (DbInstance ins : instances) {
            Integer h = ins.getHealth();
            if (h == null || h < 0) noData++;
            else if (h >= 75) healthy++;
            else if (h >= 60) warning++;
            else critical++;
        }
        int scored = instances.size() - noData;
        String healthRate = scored == 0 ? "-" : pct(healthy * 100.0 / scored) + "%";
        String overview = String.format("本次巡检共检查 %d 个数据库实例：健康 %d 个、预警 %d 个、严重 %d 个%s。健康率 %s。%s",
                instances.size(), healthy, warning, critical,
                noData > 0 ? "、无健康数据 " + noData + " 个" : "",
                healthRate,
                critical > 0 ? "存在严重问题实例，需立即处理。" : warning > 0 ? "部分实例存在预警，建议关注。" : "所有实例运行正常。");
        sections.add(summarySection("一、巡检概况", overview, List.of(
                kv("实例总数", String.valueOf(instances.size())),
                kv("健康", String.valueOf(healthy)),
                kv("预警", String.valueOf(warning)),
                kv("严重", String.valueOf(critical)),
                kv("健康率", healthRate),
                kv("统计时段", fmt(from) + " ~ " + fmt(to))
        )));

        // 2. 实例健康状态（健康分 + 活跃告警 + 核心指标最新值；指标编码按库类型分派）
        List<Map<String, Object>> healthRows = new ArrayList<>();
        for (DbInstance ins : instances) {
            boolean pg = isPostgres(ins);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", ins.getName());
            row.put("status", statusLabel(ins.getStatus()));
            row.put("health", ins.getHealth() == null || ins.getHealth() < 0 ? "-" : String.valueOf(ins.getHealth()));
            row.put("activeAlerts", activeAlerts.getOrDefault(ins.getId(), 0));
            if (pg) {
                Map<String, Double> latest = metricLatestDao.latestFrom1m(ins.getId(),
                        List.of("pg.conn.usage", "pg.tps", "pg.cache.hit_rate"));
                row.put("connUsage", fmtVal(latest.get("pg.conn.usage"), "%"));
                row.put("qps", fmtVal(latest.get("pg.tps"), ""));
                // PG 一期无语句摘要：慢查询/平均响应待二期 pg_stat_statements 接入
                row.put("slowPerMin", "-");
                row.put("latency", "-");
            } else {
                Map<String, Double> latest = metricLatestDao.latestFrom1m(ins.getId(),
                        List.of("mysql.conn.usage", "mysql.qps", "mysql.delta.slow_queries", "mysql.perf.avg_stmt_latency_ms"));
                row.put("connUsage", fmtVal(latest.get("mysql.conn.usage"), "%"));
                row.put("qps", fmtVal(latest.get("mysql.qps"), ""));
                row.put("slowPerMin", fmtVal(latest.get("mysql.delta.slow_queries"), ""));
                row.put("latency", fmtVal(latest.get("mysql.perf.avg_stmt_latency_ms"), "ms"));
            }
            healthRows.add(row);
        }
        sections.add(tableSection("二、实例健康状态", List.of(
                col("name", "实例"), col("status", "状态"), col("health", "健康分"),
                col("activeAlerts", "活跃告警"), col("connUsage", "连接使用率"),
                col("qps", "QPS/TPS"), col("slowPerMin", "慢查询/分"), col("latency", "平均响应")
        ), healthRows, "暂无实例数据"));

        // 3. 告警统计
        sections.add(buildAlertStatSection("三、告警统计", ids, from, to));

        // 4. 慢SQL Top10（各实例取 Top5 合并后按总耗时排序取 10）
        List<Map<String, Object>> slowRows = new ArrayList<>();
        for (DbInstance ins : instances) {
            for (Map<String, Object> r : queryTopSql(ins.getId(), from, to, 5)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("instance", ins.getName());
                row.put("sql", truncate(str(r.get("digest_text")), 120));
                row.put("execCount", num(r.get("exec_count")));
                row.put("avgMs", pct(num(r.get("avg_timer_wait_us")) / 1000.0));
                row.put("rowsExamined", num(r.get("rows_examined")));
                row.put("_totalWait", num(r.get("total_timer_wait")));
                slowRows.add(row);
            }
        }
        slowRows.sort(Comparator.comparingDouble((Map<String, Object> r) -> (Double) r.get("_totalWait")).reversed());
        List<Map<String, Object>> topSlow = slowRows.stream().limit(10).peek(r -> r.remove("_totalWait")).toList();
        sections.add(tableSection("四、慢SQL Top10", List.of(
                col("instance", "实例"), col("sql", "SQL 指纹"), col("execCount", "执行次数"),
                col("avgMs", "平均耗时(ms)"), col("rowsExamined", "扫描行数")
        ), topSlow, "统计时段内未发现慢SQL，数据库查询性能良好"));

        // 5. 容量趋势（表 + 30 天增长曲线图）
        List<Map<String, Object>> capRows = new ArrayList<>();
        List<Map<String, Object>> capSeries = new ArrayList<>();
        for (DbInstance ins : instances) {
            List<TsCapacityGrowthDao.CapacityGrowthPoint> trend = capacityGrowthDao.queryGrowthTrend(ins.getId(), 30);
            if (trend.isEmpty()) continue;
            TsCapacityGrowthDao.CapacityGrowthPoint last = trend.get(trend.size() - 1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instance", ins.getName());
            row.put("currentGb", gb(last.currentBytes()));
            row.put("weekGrowth", last.growthBytes() == null ? "-" : gb(last.growthBytes()));
            row.put("growthRate", last.growthRatePct() == null ? "-" : pct(last.growthRatePct()) + "%");
            capRows.add(row);
            // 每实例一条曲线：日期 → GB（保留 2 位）
            List<List<Object>> points = trend.stream().map(p -> List.<Object>of(
                    p.day().atStartOfDay(ZONE).toInstant().toEpochMilli(),
                    Math.round(p.currentBytes() / 1024.0 / 1024.0 / 1024.0 * 100) / 100.0)).toList();
            capSeries.add(Map.of("name", ins.getName(), "points", points));
        }
        sections.add(tableSection("五、容量趋势（30 天日级快照）", List.of(
                col("instance", "实例"), col("currentGb", "当前容量(GB)"),
                col("weekGrowth", "7日增长(GB)"), col("growthRate", "7日增长率")
        ), capRows, "暂无容量快照数据（日级容量汇总尚未生成）"));
        if (!capSeries.isEmpty()) {
            sections.add(chartSection("六、容量增长趋势图（30 天，GB）", "GB", capSeries, List.of(),
                    "暂无容量快照数据"));
        }

        // 6/7. 复制状态（MySQL：IO/SQL 线程 + 延迟；PG：流复制回放延迟，无线程概念）
        List<Map<String, Object>> replRows = new ArrayList<>();
        int replRisk = 0;
        for (DbInstance ins : instances) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instance", ins.getName());
            if (isPostgres(ins)) {
                Map<String, Double> repl = metricLatestDao.latestFrom1m(ins.getId(), List.of(
                        "pg.repl.is_replica", "pg.repl.lag_seconds", "pg.repl.replica_count"));
                Double isReplica = repl.get("pg.repl.is_replica");
                if (isReplica == null) {
                    row.put("role", "-");
                    row.put("io", "-");
                    row.put("sql", "-");
                    row.put("lag", "-");
                    row.put("conclusion", "无复制指标数据");
                } else if (isReplica < 0.5) {
                    double replicaCount = num(repl.get("pg.repl.replica_count"));
                    row.put("role", replicaCount >= 1 ? "主库（下游从库 " + Math.round(replicaCount) + " 个）" : "主库/独立");
                    row.put("io", "-");
                    row.put("sql", "-");
                    row.put("lag", "-");
                    row.put("conclusion", replicaCount >= 1 ? "主库正常发送 WAL" : "未启用复制");
                } else {
                    double lag = num(repl.get("pg.repl.lag_seconds"));
                    row.put("role", "从库");
                    row.put("io", "-");
                    row.put("sql", "-");
                    row.put("lag", pct(lag) + "s");
                    if (lag > 60) replRisk++;
                    row.put("conclusion", lag > 60 ? "复制回放延迟偏高，建议关注" : "复制正常");
                }
            } else {
                Map<String, Double> repl = metricLatestDao.latestFrom1m(ins.getId(), List.of(
                        "mysql.replication.is_replica", "mysql.replication.seconds_behind",
                        "mysql.replication.io_running", "mysql.replication.sql_running"));
                Double isReplica = repl.get("mysql.replication.is_replica");
                if (isReplica == null) {
                    row.put("role", "-");
                    row.put("io", "-");
                    row.put("sql", "-");
                    row.put("lag", "-");
                    row.put("conclusion", "无复制指标数据");
                } else if (isReplica < 0.5) {
                    row.put("role", "主库/独立");
                    row.put("io", "-");
                    row.put("sql", "-");
                    row.put("lag", "-");
                    row.put("conclusion", "未启用复制");
                } else {
                    boolean ioOk = num(repl.get("mysql.replication.io_running")) >= 0.5;
                    boolean sqlOk = num(repl.get("mysql.replication.sql_running")) >= 0.5;
                    double lag = num(repl.get("mysql.replication.seconds_behind"));
                    row.put("role", "从库");
                    row.put("io", ioOk ? "运行中" : "已停止");
                    row.put("sql", sqlOk ? "运行中" : "已停止");
                    row.put("lag", pct(lag) + "s");
                    boolean risk = !ioOk || !sqlOk || lag > 60;
                    if (risk) replRisk++;
                    row.put("conclusion", !ioOk || !sqlOk ? "复制线程异常，需立即处理"
                            : lag > 60 ? "复制延迟偏高，建议关注" : "复制正常");
                }
            }
            replRows.add(row);
        }
        sections.add(tableSection("七、复制状态", List.of(
                col("instance", "实例"), col("role", "角色"), col("io", "IO 线程"),
                col("sql", "SQL 线程"), col("lag", "复制延迟"), col("conclusion", "结论")
        ), replRows, "暂无实例数据"));

        // 8. 采集健康（collect_log 最新状态 + 24h 成功率）
        Map<Long, String> nameById = instances.stream()
                .collect(Collectors.toMap(DbInstance::getId, DbInstance::getName, (a, b) -> a));
        List<Map<String, Object>> collectRows = new ArrayList<>();
        int collectRisk = 0;
        for (Map<String, Object> t : collectLogMapper.selectLatestPerTask()) {
            Long insId = t.get("instance_id") instanceof Number n ? n.longValue() : null;
            if (insId == null || !nameById.containsKey(insId)) continue;
            long total = (long) num(t.get("total_24h"));
            long success = (long) num(t.get("success_24h"));
            double rate = total == 0 ? 100 : success * 100.0 / total;
            boolean lastSuccess = Boolean.TRUE.equals(t.get("last_success"));
            if (!lastSuccess || rate < 95) collectRisk++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instance", nameById.get(insId));
            row.put("frequency", str(t.get("frequency")));
            row.put("lastTime", str(t.get("last_collect_time")).length() >= 19
                    ? str(t.get("last_collect_time")).substring(0, 19) : str(t.get("last_collect_time")));
            row.put("result", lastSuccess ? "成功" : "失败：" + truncate(str(t.get("last_error_message")), 60));
            row.put("rate", pct(rate) + "%");
            collectRows.add(row);
        }
        collectRows.sort(Comparator.comparing(r -> String.valueOf(r.get("instance"))));
        sections.add(tableSection("八、采集健康（近 24 小时）", List.of(
                col("instance", "实例"), col("frequency", "频率"), col("lastTime", "最近采集"),
                col("result", "最近结果"), col("rate", "24h 成功率")
        ), collectRows, "暂无采集记录"));

        // 9. 优化建议（按巡检发现推导）
        List<String> tips = new ArrayList<>();
        if (critical > 0) tips.add("存在 " + critical + " 个严重状态实例，请优先进入告警事件中心处理未恢复事件。");
        if (warning > 0) tips.add(warning + " 个实例健康分低于 75，建议进入实例的性能分析页排查扣分维度。");
        if (!topSlow.isEmpty()) tips.add("统计时段内存在慢SQL，建议对 Top 慢SQL 进行执行计划分析和索引优化。");
        for (Map<String, Object> row : capRows) {
            String rate = String.valueOf(row.get("growthRate"));
            if (rate.endsWith("%") && parseDouble(rate.substring(0, rate.length() - 1)) > 10) {
                tips.add("实例「" + row.get("instance") + "」7 日容量增长率超过 10%，建议评估磁盘余量并规划归档清理。");
            }
        }
        if (replRisk > 0) tips.add(replRisk + " 个从库存在复制线程异常或延迟偏高，请优先检查复制链路（线程状态、位点差、网络与 I/O）。");
        if (collectRisk > 0) tips.add(collectRisk + " 个采集任务最近失败或 24h 成功率低于 95%，请在「采集任务管理」检查账号权限与网络连通性。");
        if (tips.isEmpty()) tips.add("本次巡检未发现明显风险，请保持当前监控与告警策略。");
        sections.add(listSection("九、优化建议", tips));
        return sections;
    }

    // ── 性能分析报告（单实例） ────────────────────────────────────────────────

    private List<Map<String, Object>> buildPerformanceSections(DbInstance ins,
                                                               OffsetDateTime from, OffsetDateTime to) {
        List<Map<String, Object>> sections = new ArrayList<>();
        Timestamp tFrom = Timestamp.from(from.toInstant());
        Timestamp tTo = Timestamp.from(to.toInstant());

        // 指标口径按库类型分派（PG 一期无语句延迟/慢SQL，对应段落自动降级）
        boolean pg = isPostgres(ins);
        LinkedHashMap<String, String[]> coreMetrics = pg ? PG_CORE_METRICS : CORE_METRICS;
        String throughputCode = pg ? "pg.tps" : "mysql.qps";
        String connUsageCode = pg ? "pg.conn.usage" : "mysql.conn.usage";
        String hitRateCode = pg ? "pg.cache.hit_rate" : "mysql.innodb.buffer_pool_hit_rate";
        String hitRateLabel = pg ? "缓存" : "Buffer Pool";

        List<String> codes = List.copyOf(coreMetrics.keySet());
        Map<String, Map<String, Object>> stats = statsMapper.selectMetricStats(ins.getId(), codes, tFrom, tTo)
                .stream().collect(Collectors.toMap(r -> str(r.get("metric_code")), r -> r, (a, b) -> a));

        // 1. 分析概述
        double avgQps = statVal(stats, throughputCode, "avg_value");
        double maxConnUsage = statVal(stats, connUsageCode, "max_value");
        double avgLatency = pg ? 0 : statVal(stats, "mysql.perf.avg_stmt_latency_ms", "avg_value");
        double avgHit = statVal(stats, hitRateCode, "avg_value");
        boolean hasData = !stats.isEmpty();
        String summary = !hasData
                ? "统计时段内无采集数据，无法进行性能分析。请检查实例采集状态。"
                : pg
                ? String.format("实例「%s」统计时段内平均 TPS %s，连接使用率峰值 %s%%，缓存命中率 %s%%。%s",
                        ins.getName(), pct(avgQps), pct(maxConnUsage), pct(avgHit),
                        maxConnUsage > 80 ? "连接压力偏高，需要关注。"
                                : avgHit > 0 && avgHit < 95 ? "缓存命中率偏低，建议评估 shared_buffers 配置。"
                                : "整体运行平稳。")
                : String.format("实例「%s」统计时段内平均 QPS %s，连接使用率峰值 %s%%，平均响应时间 %s ms，Buffer Pool 命中率 %s%%。%s",
                        ins.getName(), pct(avgQps), pct(maxConnUsage), pct(avgLatency), pct(avgHit),
                        maxConnUsage > 80 ? "连接压力偏高，需要关注。"
                                : avgHit > 0 && avgHit < 95 ? "缓存命中率偏低，建议评估 Buffer Pool 配置。"
                                : "整体运行平稳。");
        sections.add(summarySection("一、分析概述", summary, List.of(
                kv("实例", ins.getName() + "（" + ins.getHost() + ":" + ins.getPort() + "）"),
                kv("健康分", ins.getHealth() == null || ins.getHealth() < 0 ? "-" : String.valueOf(ins.getHealth())),
                kv("统计时段", fmt(from) + " ~ " + fmt(to))
        )));

        // 2. 指标统计
        List<Map<String, Object>> metricRows = new ArrayList<>();
        coreMetrics.forEach((code, meta) -> {
            Map<String, Object> s = stats.get(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metric", meta[0]);
            if (s == null) {
                row.put("avg", "-");
                row.put("max", "-");
                row.put("min", "-");
            } else {
                row.put("avg", fmtVal(num(s.get("avg_value")), meta[1]));
                row.put("max", fmtVal(num(s.get("max_value")), meta[1]));
                row.put("min", fmtVal(num(s.get("min_value")), meta[1]));
            }
            metricRows.add(row);
        });
        sections.add(tableSection("二、指标统计", List.of(
                col("metric", "指标"), col("avg", "平均值"), col("max", "最大值"), col("min", "最小值")
        ), hasData ? metricRows : List.of(), "统计时段内无采集数据"));

        // 3. 核心指标趋势图（24h 用分钟级，7d/30d 用小时级汇总）
        long fromMs = from.toInstant().toEpochMilli();
        long toMs = to.toInstant().toEpochMilli();
        String freq = (toMs - fromMs) > 26L * 3600 * 1000 ? "1h" : "1m";
        int chartIdx = 0;
        List<String> chartCodes = pg
                ? List.of("pg.tps", "pg.cache.hit_rate", "pg.conn.usage")
                : List.of("mysql.qps", "mysql.perf.avg_stmt_latency_ms", "mysql.conn.usage");
        for (String code : chartCodes) {
            String[] meta = coreMetrics.get(code);
            MetricTrendVo trend = metricQueryService.metricTrend(ins.getId(), code, fromMs, toMs, freq);
            List<List<Object>> points = trend.getPoints() == null ? List.of()
                    : trend.getPoints().stream().map(p -> List.<Object>of(p.getTs(), p.getValue())).toList();
            sections.add(chartSection("三、核心指标趋势（" + (++chartIdx) + "）：" + meta[0], meta[1],
                    List.of(Map.of("name", meta[0], "points", points)), List.of(),
                    "统计时段内无该指标采集数据"));
        }

        // 4. 异常时段识别（小时均值超过全期均值 1.5 倍视为异常）
        List<Map<String, Object>> anomalyRows = new ArrayList<>();
        List<String> anomalyCodes = pg
                ? List.of("pg.tps", "pg.conn.usage", "pg.delta.temp_files", "pg.locks.waiting")
                : List.of("mysql.qps", "mysql.perf.avg_stmt_latency_ms", "mysql.delta.slow_queries", "mysql.conn.usage");
        for (String code : anomalyCodes) {
            double overall = statVal(stats, code, "avg_value");
            if (overall <= 0) continue;
            String label = coreMetrics.get(code)[0];
            String unit = coreMetrics.get(code)[1];
            for (Map<String, Object> h : statsMapper.selectHourlyAvg(ins.getId(), code, tFrom, tTo)) {
                double v = num(h.get("avg_value"));
                if (v > overall * 1.5 && v - overall > 0.01) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Object hourTs = h.get("hour_ts");
                    row.put("hour", hourTs == null ? "-" : String.valueOf(hourTs).substring(0, Math.min(16, String.valueOf(hourTs).length())));
                    row.put("metric", label);
                    row.put("value", fmtVal(v, unit));
                    row.put("baseline", fmtVal(overall, unit));
                    row.put("ratio", pct(v / overall) + " 倍");
                    anomalyRows.add(row);
                }
            }
        }
        anomalyRows.sort(Comparator.comparing(r -> String.valueOf(r.get("hour"))));
        sections.add(tableSection("四、异常时段识别（小时均值超全期均值 1.5 倍）", List.of(
                col("hour", "时段"), col("metric", "指标"), col("value", "时段均值"),
                col("baseline", "全期均值"), col("ratio", "倍数")
        ), anomalyRows.stream().limit(20).toList(), "未识别到明显异常时段，负载分布平稳"));

        // 4. 性能瓶颈分析（按库类型输出对应结论）
        List<String> bottlenecks = new ArrayList<>();
        if (maxConnUsage > 80) bottlenecks.add("连接使用率峰值 " + pct(maxConnUsage) + "%，接近连接上限，存在连接池耗尽风险。");
        if (avgHit > 0 && avgHit < 95) bottlenecks.add(hitRateLabel + " 命中率均值 " + pct(avgHit) + "%，低于 95% 参考线，可能存在内存不足或全表扫描。");
        double slowSum = 0;
        if (pg) {
            double tempFiles = statVal(stats, "pg.delta.temp_files", "avg_value");
            double deadlocks = statVal(stats, "pg.delta.deadlocks", "avg_value");
            if (tempFiles > 0.5) bottlenecks.add("平均每分钟产生临时文件 " + pct(tempFiles) + " 个，存在超出 work_mem 的大排序/哈希，建议定位相关语句。");
            if (deadlocks > 0) bottlenecks.add("统计时段内发生死锁（平均 " + pct(deadlocks) + " 次/分钟），建议复盘事务加锁顺序。");
        } else {
            if (avgLatency > 100) bottlenecks.add("平均响应时间 " + pct(avgLatency) + " ms 偏高，建议结合慢SQL分析定位耗时来源。");
            slowSum = statVal(stats, "mysql.delta.slow_queries", "avg_value");
            if (slowSum > 0.5) bottlenecks.add("慢查询平均每分钟新增 " + pct(slowSum) + " 条，SQL 质量需要治理。");
        }
        if (bottlenecks.isEmpty()) bottlenecks.add(hasData ? "各核心指标均在合理范围内，未发现明显性能瓶颈。" : "无数据，无法判断瓶颈。");
        sections.add(listSection("五、性能瓶颈分析", bottlenecks));

        // 5. 优化建议
        List<String> tips = new ArrayList<>();
        if (maxConnUsage > 80) tips.add("核对应用连接池上限与数据库 max_connections 配置，清理长时间空闲连接。");
        if (pg) {
            if (avgHit > 0 && avgHit < 95) tips.add("评估 shared_buffers / effective_cache_size 是否与数据热集匹配，排查高频全表扫描 SQL。");
            double tempFiles = statVal(stats, "pg.delta.temp_files", "avg_value");
            if (tempFiles > 0.5) tips.add("开启 log_temp_files 定位产生临时文件的语句，优先优化 SQL，再评估 work_mem。");
        } else {
            if (avgHit > 0 && avgHit < 95) tips.add("评估 innodb_buffer_pool_size 是否与数据热集匹配，排查高频全表扫描 SQL。");
            if (slowSum > 0.5 || avgLatency > 100) tips.add("进入慢SQL分析页处理 Top 慢SQL：补充索引、改写查询、刷新统计信息。");
        }
        tips.add("建议保持定期巡检报告，跟踪指标趋势变化。");
        sections.add(listSection("六、优化建议", tips));
        return sections;
    }

    // ── 告警分析报告 ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildAlertSections(List<DbInstance> instances,
                                                         OffsetDateTime from, OffsetDateTime to) {
        List<Map<String, Object>> sections = new ArrayList<>();
        List<Long> ids = instances.stream().map(DbInstance::getId).toList();
        Timestamp tFrom = Timestamp.from(from.toInstant());
        Timestamp tTo = Timestamp.from(to.toInstant());

        Map<String, Object> summary = statsMapper.selectAlertSummary(ids, tFrom, tTo);
        long total = (long) num(summary == null ? null : summary.get("total"));
        long active = (long) num(summary == null ? null : summary.get("active_cnt"));
        long recovered = (long) num(summary == null ? null : summary.get("recovered_cnt"));
        long closed = (long) num(summary == null ? null : summary.get("closed_cnt"));

        // 1. 告警概况
        String overview = total == 0
                ? "统计时段内未产生告警事件，系统运行平稳。"
                : String.format("统计时段内共产生 %d 条告警事件，其中未恢复 %d 条、已恢复 %d 条、已关闭 %d 条。%s",
                        total, active, recovered, closed,
                        active > 0 ? "请优先处理未恢复事件。" : "全部事件已闭环。");
        sections.add(summarySection("一、告警概况", overview, List.of(
                kv("告警总数", String.valueOf(total)),
                kv("未恢复", String.valueOf(active)),
                kv("已恢复", String.valueOf(recovered)),
                kv("涉及实例", String.valueOf(instances.size())),
                kv("统计时段", fmt(from) + " ~ " + fmt(to))
        )));

        // 2. 级别分布
        Map<String, String> levelLabels = dictLabels("alert_level");
        List<Map<String, Object>> levelRows = statsMapper.selectAlertLevelStats(ids, tFrom, tTo).stream()
                .sorted(Comparator.comparing(r -> str(r.get("rule_level"))))
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("level", levelLabels.getOrDefault(str(r.get("rule_level")), str(r.get("rule_level"))));
                    row.put("count", (long) num(r.get("cnt")));
                    row.put("active", (long) num(r.get("active_cnt")));
                    return row;
                }).toList();
        sections.add(tableSection("二、告警级别分布", List.of(
                col("level", "级别"), col("count", "事件数"), col("active", "未恢复")
        ), levelRows, "统计时段内无告警事件"));

        // 3. 高频告警实例
        List<Map<String, Object>> instRows = statsMapper.selectAlertTopInstances(ids, tFrom, tTo, 10).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("instance", str(r.get("instance_name")));
                    row.put("count", (long) num(r.get("cnt")));
                    row.put("severe", (long) num(r.get("severe_cnt")));
                    row.put("active", (long) num(r.get("active_cnt")));
                    return row;
                }).toList();
        sections.add(tableSection("三、高频告警实例 Top10", List.of(
                col("instance", "实例"), col("count", "事件数"), col("severe", "一/二级"), col("active", "未恢复")
        ), instRows, "统计时段内无告警事件"));

        // 4. Top 告警规则与根因方向（结合下钻画像库）
        List<Map<String, Object>> ruleRows = statsMapper.selectAlertTopRules(ids, tFrom, tTo, 10).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("rule", str(r.get("rule_name")));
                    row.put("level", levelLabels.getOrDefault(str(r.get("rule_level")), str(r.get("rule_level"))));
                    row.put("count", (long) num(r.get("cnt")));
                    String metricCode = str(r.get("metric_code"));
                    DrilldownProfileVo profile = StringUtils.hasText(metricCode)
                            ? drilldownProfileService.match(metricCode) : null;
                    row.put("category", profile == null ? "-" : profile.getProfileLabel());
                    return row;
                }).toList();
        sections.add(tableSection("四、Top 告警规则与根因方向", List.of(
                col("rule", "告警规则"), col("level", "级别"), col("count", "触发次数"), col("category", "根因方向（画像）")
        ), ruleRows, "统计时段内无告警事件"));

        // 5. 处理建议
        List<String> tips = new ArrayList<>();
        if (active > 0) tips.add("存在 " + active + " 条未恢复事件，请进入告警事件中心确认并处理（可点击告警信息进入下钻分析）。");
        if (!ruleRows.isEmpty()) {
            Map<String, Object> top = ruleRows.get(0);
            tips.add("触发最频繁的规则为「" + top.get("rule") + "」（" + top.get("count") + " 次，" + top.get("category")
                    + "），建议按下钻页排查路径处理根因；若属于业务正常波动，可调整该规则阈值降噪。");
        }
        if (!instRows.isEmpty()) {
            tips.add("实例「" + instRows.get(0).get("instance") + "」告警最多，建议对该实例做专项性能分析。");
        }
        if (tips.isEmpty()) tips.add("统计时段内无告警，可适当回顾告警规则覆盖度，确保关键风险均有规则值守。");
        sections.add(listSection("五、处理建议", tips));
        return sections;
    }

    // ── 安全专项报告 ─────────────────────────────────────────────────────────

    /**
     * 安全专项报告（§15.4 安全监控汇总）：SSL / 弱账号 / 认证失败与暴力破解 /
     * 白名单外来源 / 权限变更与危险操作 / 审计插件对接状态，多实例范围。
     */
    private List<Map<String, Object>> buildSecuritySections(List<DbInstance> instances,
                                                            OffsetDateTime from, OffsetDateTime to) {
        List<Map<String, Object>> sections = new ArrayList<>();
        Timestamp tFrom = Timestamp.from(from.toInstant());
        Timestamp tTo = Timestamp.from(to.toInstant());

        List<String> latest1mCodes = List.of(
                "mysql.security.brute_force_suspect", "mysql.security.unknown_source_count");
        List<String> latest1dCodes = List.of(
                "mysql.security.ssl_enabled", "mysql.security.ssl_cert_days_left",
                "mysql.security.audit_plugin_active",
                "mysql.security.empty_password_count", "mysql.security.anonymous_user_count",
                "mysql.security.any_host_account_count", "mysql.security.super_priv_count");
        List<String> sumCodes = List.of(
                "mysql.security.auth_fail_delta", "mysql.security.priv_change_delta",
                "mysql.security.dangerous_op_delta");

        int sslOff = 0, certExpiring = 0, bruteForce = 0, unknownSrc = 0, noAudit = 0, weakAccount = 0;
        List<Map<String, Object>> statusRows = new ArrayList<>();
        List<Map<String, Object>> eventRows = new ArrayList<>();
        List<Map<String, Object>> auditRows = new ArrayList<>();
        long totalAuthFail = 0, totalPrivChange = 0, totalDangerousOp = 0;

        for (DbInstance ins : instances) {
            Map<String, Double> m1 = metricLatestDao.latestFrom1m(ins.getId(), latest1mCodes);
            Map<String, Double> d1 = metricLatestDao.latestFrom1d(ins.getId(), latest1dCodes);

            Double sslEnabled = d1.get("mysql.security.ssl_enabled");
            Double certDays = d1.get("mysql.security.ssl_cert_days_left");
            Double auditActive = d1.get("mysql.security.audit_plugin_active");
            double weak = num(d1.get("mysql.security.empty_password_count"))
                    + num(d1.get("mysql.security.anonymous_user_count"))
                    + num(d1.get("mysql.security.any_host_account_count"));
            boolean brute = num(m1.get("mysql.security.brute_force_suspect")) >= 1;
            double unknown = num(m1.get("mysql.security.unknown_source_count"));

            if (sslEnabled != null && sslEnabled < 0.5) sslOff++;
            if (certDays != null && certDays <= 30) certExpiring++;
            if (auditActive == null || auditActive < 0.5) noAudit++;
            if (brute) bruteForce++;
            if (unknown > 0) unknownSrc++;
            if (weak > 0) weakAccount++;

            List<String> risks = new ArrayList<>();
            if (brute) risks.add("疑似暴力破解");
            if (unknown > 0) risks.add("存在白名单外来源");
            if (weak > 0) risks.add("存在弱配置账号");
            if (certDays != null && certDays <= 30) risks.add("SSL 证书即将到期");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instance", ins.getName());
            row.put("ssl", sslEnabled == null ? "-" : sslEnabled >= 0.5 ? "已启用" : "未启用");
            row.put("certDays", certDays == null ? "-" : Math.round(certDays) + " 天");
            row.put("audit", auditActive == null ? "未检测" : auditActive >= 0.5 ? "插件审计" : "轻审计");
            row.put("weakAccount", d1.containsKey("mysql.security.empty_password_count")
                    || d1.containsKey("mysql.security.anonymous_user_count")
                    || d1.containsKey("mysql.security.any_host_account_count")
                    ? String.valueOf(Math.round(weak)) : "-");
            row.put("superPriv", d1.get("mysql.security.super_priv_count") == null
                    ? "-" : String.valueOf(Math.round(num(d1.get("mysql.security.super_priv_count")))));
            row.put("conclusion", risks.isEmpty() ? "正常" : String.join("；", risks));
            statusRows.add(row);

            // 窗口内安全事件合计（delta 计数器求和）
            Map<String, Double> sums = new LinkedHashMap<>();
            for (Map<String, Object> r : statsMapper.selectMetricSums(ins.getId(), sumCodes, tFrom, tTo)) {
                sums.put(str(r.get("metric_code")), num(r.get("sum_value")));
            }
            long authFail = Math.round(num(sums.get("mysql.security.auth_fail_delta")));
            long privChange = Math.round(num(sums.get("mysql.security.priv_change_delta")));
            long dangerousOp = Math.round(num(sums.get("mysql.security.dangerous_op_delta")));
            totalAuthFail += authFail;
            totalPrivChange += privChange;
            totalDangerousOp += dangerousOp;
            Map<String, Object> evRow = new LinkedHashMap<>();
            evRow.put("instance", ins.getName());
            evRow.put("authFail", authFail);
            evRow.put("privChange", privChange);
            evRow.put("dangerousOp", dangerousOp);
            evRow.put("unknownSource", Math.round(unknown));
            eventRows.add(evRow);

            // 敏感语句审计明细（最近一批，每实例最多 10 条）
            auditRows.addAll(parseAuditEvents(ins));
        }

        // 1. 安全概况
        List<String> findings = new ArrayList<>();
        if (bruteForce > 0) findings.add(bruteForce + " 个实例疑似遭遇暴力破解");
        if (unknownSrc > 0) findings.add(unknownSrc + " 个实例存在白名单外访问来源");
        if (weakAccount > 0) findings.add(weakAccount + " 个实例存在弱配置账号（空密码/匿名/任意主机）");
        if (certExpiring > 0) findings.add(certExpiring + " 个实例 SSL 证书 30 天内到期");
        if (totalDangerousOp > 0) findings.add("窗口内发生 " + totalDangerousOp + " 次危险操作语句");
        String overview = String.format("本次安全专项检查覆盖 %d 个实例。%s", instances.size(),
                findings.isEmpty() ? "未发现明显安全风险，各实例安全基线状态良好。"
                        : "发现以下风险需要关注：" + String.join("；", findings) + "。");
        sections.add(summarySection("一、安全概况", overview, List.of(
                kv("检查实例数", String.valueOf(instances.size())),
                kv("SSL 未启用", String.valueOf(sslOff)),
                kv("暴力破解疑似", String.valueOf(bruteForce)),
                kv("白名单外来源", String.valueOf(unknownSrc)),
                kv("认证失败合计", String.valueOf(totalAuthFail)),
                kv("权限变更合计", String.valueOf(totalPrivChange)),
                kv("统计时段", fmt(from) + " ~ " + fmt(to))
        )));

        // 2. 实例安全状态
        sections.add(tableSection("二、实例安全状态", List.of(
                col("instance", "实例"), col("ssl", "SSL"), col("certDays", "证书剩余"),
                col("audit", "审计方式"), col("weakAccount", "弱配置账号"),
                col("superPriv", "SUPER 账号"), col("conclusion", "结论")
        ), statusRows, "暂无实例数据"));

        // 3. 窗口内安全事件统计
        sections.add(tableSection("三、安全事件统计（统计时段内）", List.of(
                col("instance", "实例"), col("authFail", "认证失败"), col("privChange", "权限变更"),
                col("dangerousOp", "危险操作"), col("unknownSource", "白名单外连接")
        ), eventRows, "统计时段内无安全事件数据"));

        // 4. 认证失败趋势（单实例范围时输出趋势图）
        if (instances.size() == 1) {
            DbInstance first = instances.get(0);
            long fromMs = from.toInstant().toEpochMilli();
            long toMs = to.toInstant().toEpochMilli();
            String freq = (toMs - fromMs) > 26L * 3600 * 1000 ? "1h" : "1m";
            MetricTrendVo trend = metricQueryService.metricTrend(
                    first.getId(), "mysql.security.auth_fail_delta", fromMs, toMs, freq);
            List<List<Object>> points = trend.getPoints() == null ? List.of()
                    : trend.getPoints().stream().map(p -> List.<Object>of(p.getTs(), p.getValue())).toList();
            sections.add(chartSection("四、认证失败趋势", "次", List.of(
                    Map.of("name", "认证失败次数", "points", points)), List.of(),
                    "统计时段内无认证失败数据"));
        }

        // 5. 敏感语句审计明细
        String auditTitle = instances.size() == 1 ? "五、敏感语句审计明细（最近一批）" : "四、敏感语句审计明细（最近一批）";
        sections.add(tableSection(auditTitle, List.of(
                col("instance", "实例"), col("type", "类型"), col("text", "语句指纹"),
                col("count", "次数"), col("lastSeen", "最近执行")
        ), auditRows.stream().limit(20).toList(), "未捕获到权限变更/危险操作语句"));

        // 6. 安全加固建议
        List<String> tips = new ArrayList<>();
        if (bruteForce > 0) tips.add("疑似暴力破解的实例请核实认证失败来源 IP：确认为攻击时在防火墙封禁来源，并检查相关账号密码强度。");
        if (unknownSrc > 0) tips.add("存在白名单外来源的实例请核实是否为合法新应用：合法则将来源登记进实例的连接来源白名单，否则排查访问路径。");
        if (weakAccount > 0) tips.add("清理弱配置账号：为空密码账号设置强密码、删除匿名账号、将 '%' 任意主机账号收敛为具体网段。");
        if (sslOff > 0) tips.add(sslOff + " 个实例未启用 SSL：跨网段/公网访问的实例建议启用传输加密（内网专用实例可按安全策略评估）。");
        if (certExpiring > 0) tips.add("SSL 证书 30 天内到期的实例请提前更换证书，避免到期后客户端连接失败。");
        if (noAudit > 0) tips.add(noAudit + " 个实例未安装数据库审计插件，当前使用平台轻审计（无法还原执行账号）。"
                + "有合规要求的实例建议安装 Percona Audit Log 或 MariaDB Audit Plugin，安装后平台自动识别。");
        if (totalPrivChange > 0) tips.add("统计时段内发生 " + totalPrivChange + " 次权限变更，请与变更工单核对，确认均为授权操作。");
        if (tips.isEmpty()) tips.add("本次检查未发现明显安全风险，建议保持安全基线巡检频率，并定期回顾账号与白名单配置。");
        String tipsTitle = instances.size() == 1 ? "六、安全加固建议" : "五、安全加固建议";
        sections.add(listSection(tipsTitle, tips));
        return sections;
    }

    /** 解析实例最新一批敏感语句审计明细（mysql.security.audit_events 文本指标 JSON）。 */
    private List<Map<String, Object>> parseAuditEvents(DbInstance ins) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            var textVo = metricQueryService.latestTextMetrics(ins.getId(),
                    List.of("mysql.security.audit_events"), "1m");
            String json = textVo.getValues() == null ? null : textVo.getValues().get("mysql.security.audit_events");
            if (!StringUtils.hasText(json)) {
                return rows;
            }
            cn.hutool.json.JSONArray arr = cn.hutool.json.JSONUtil.parseArray(json);
            for (int i = 0; i < arr.size() && i < 10; i++) {
                cn.hutool.json.JSONObject o = arr.getJSONObject(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("instance", ins.getName());
                row.put("type", "priv".equals(o.getStr("type")) ? "权限变更" : "危险操作");
                row.put("text", truncate(o.getStr("text", ""), 100));
                row.put("count", o.getLong("count", 0L));
                row.put("lastSeen", o.getStr("lastSeen", "-"));
                rows.add(row);
            }
        } catch (Exception e) {
            log.warn("解析审计明细失败 instanceId={}: {}", ins.getId(), e.getMessage());
        }
        return rows;
    }

    /** 告警统计段（巡检报告复用）：级别分布 + Top 规则合并为一段表格。 */
    private Map<String, Object> buildAlertStatSection(String title, List<Long> ids,
                                                      OffsetDateTime from, OffsetDateTime to) {
        Timestamp tFrom = Timestamp.from(from.toInstant());
        Timestamp tTo = Timestamp.from(to.toInstant());
        Map<String, String> levelLabels = dictLabels("alert_level");
        List<Map<String, Object>> rows = statsMapper.selectAlertTopRules(ids, tFrom, tTo, 10).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("rule", str(r.get("rule_name")));
                    row.put("level", levelLabels.getOrDefault(str(r.get("rule_level")), str(r.get("rule_level"))));
                    row.put("count", (long) num(r.get("cnt")));
                    return row;
                }).toList();
        return tableSection(title + "（Top 告警规则）", List.of(
                col("rule", "告警规则"), col("level", "级别"), col("count", "事件数")
        ), rows, "统计时段内无告警事件");
    }

    // ── 事件诊断报告（§11.7：事件报告后端生成并归档） ──────────────────────────

    /** 生成告警事件诊断报告并归档（由告警下钻页触发）。 */
    private Long generateEventReport(Long eventId) {
        if (eventId == null) {
            throw new BusinessException("事件报告必须指定告警事件 ID");
        }
        AlertEvent event = alertEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("告警事件不存在: " + eventId);
        }
        DataScope ds = dataScopeService.currentScope();
        if (event.getInstanceId() != null && !ds.allows(event.getInstanceId())) {
            throw new BusinessException("无权访问实例: " + event.getInstanceId());
        }

        MonitorReport report = new MonitorReport();
        report.setReportCode("EVT-" + System.currentTimeMillis());
        report.setTitle("告警事件诊断报告 - " + event.getEventCode());
        report.setReportType("event");
        report.setScopeType("instance");
        report.setScopeText("实例：" + str(event.getInstanceName()));
        report.setInstanceIds(event.getInstanceId() == null ? List.of() : List.of(event.getInstanceId()));
        report.setTimeRange("24h");
        report.setGenMode("manual");
        report.setStatus("archived");
        report.setContent(Map.of("sections", buildEventSections(event)));
        report.setCreatedBy(currentUserName());
        report.setGenerateTime(OffsetDateTime.now());
        reportMapper.insert(report);
        return report.getId();
    }

    private List<Map<String, Object>> buildEventSections(AlertEvent event) {
        List<Map<String, Object>> sections = new ArrayList<>();
        boolean scenario = "scenario".equals(event.getEventSource()) && StringUtils.hasText(event.getScenarioCode());

        // 规则、触发指标与画像匹配键（与下钻页同一口径）
        AlertRule rule = event.getRuleId() == null ? null : alertRuleMapper.selectById(event.getRuleId());
        String metricCode = rule != null ? rule.getMetricName()
                : isConnectionFailureEvent(event) ? Constants.SYSTEM_RULE_CONNECTION_FAILURE : null;
        String operator = null;
        if (rule != null && rule.getConditionConfig() != null) {
            Object op = rule.getConditionConfig().get("operator");
            operator = op == null ? null : String.valueOf(op);
        }
        MetricDefinition metricDef = StringUtils.hasText(metricCode)
                ? metricDefinitionMapper.selectOne(new LambdaQueryWrapper<MetricDefinition>()
                        .eq(MetricDefinition::getMetricCode, metricCode))
                : null;
        String unit = metricDef == null ? "" : unitSymbol(metricDef.getUnit());
        String metricLabel = metricDef != null ? metricDef.getMetricName() : str(metricCode);
        DrilldownProfileVo profile = drilldownProfileService.match(scenario ? event.getScenarioCode() : metricCode);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime endRef = event.getRecoveryTime() != null ? event.getRecoveryTime() : now;
        long durationSec = Math.max(0, endRef.toEpochSecond()
                - (event.getTriggerTime() == null ? endRef.toEpochSecond() : event.getTriggerTime().toEpochSecond()));
        Map<String, String> levelLabels = dictLabels("alert_level");
        Map<String, String> statusLabels = dictLabels("alert_event_status");

        // 1. 事件摘要
        String valueText = scenario
                ? "多信号综合命中"
                : str(event.getTriggerValue()) + unit
                        + (StringUtils.hasText(event.getThresholdValue())
                                ? "（阈值 " + str(operator) + " " + event.getThresholdValue() + unit + "）" : "");
        sections.add(summarySection("一、事件摘要",
                String.format("实例「%s」于 %s 触发「%s」（%s）告警，%s。%s",
                        str(event.getInstanceName()), fmt(event.getTriggerTime()), str(event.getRuleName()),
                        levelLabels.getOrDefault(str(event.getRuleLevel()), str(event.getRuleLevel())),
                        scenario ? "该事件为场景综合诊断事件（多信号联合判断）" : "触发指标为「" + metricLabel + "」",
                        event.getRecoveryTime() != null
                                ? "事件已于 " + fmt(event.getRecoveryTime()) + " 恢复。"
                                : "截至报告生成时事件尚未恢复。"),
                List.of(
                        kv("事件编码", str(event.getEventCode())),
                        kv("告警规则", str(event.getRuleName())),
                        kv("告警级别", levelLabels.getOrDefault(str(event.getRuleLevel()), str(event.getRuleLevel()))),
                        kv("触发实例", str(event.getInstanceName())),
                        kv("触发时间", str(fmt(event.getTriggerTime()))),
                        kv("持续时间", fmtDuration(durationSec)),
                        kv("当前状态", statusLabels.getOrDefault(str(event.getStatus()), str(event.getStatus()))),
                        kv("触发值/阈值", valueText),
                        kv("告警信息", str(event.getAlertMessage()))
                )));

        // 2. 关联指标趋势（告警前后 30 分钟，chart 分段：预览页 ECharts 渲染、导出 Word 嵌图）
        List<Map<String, Object>> trendMetrics = resolveTrendMetrics(profile, metricCode, metricLabel, unit);
        long fromMs = (event.getTriggerTime() == null ? now : event.getTriggerTime()).minusMinutes(30).toInstant().toEpochMilli();
        long toMs = Math.min(now.toInstant().toEpochMilli(), endRef.plusMinutes(30).toInstant().toEpochMilli());
        List<Map<String, Object>> markers = new ArrayList<>();
        if (event.getTriggerTime() != null) {
            markers.add(marker(event.getTriggerTime().toInstant().toEpochMilli(), "告警触发", "#e64545"));
        }
        if (event.getRecoveryTime() != null) {
            markers.add(marker(event.getRecoveryTime().toInstant().toEpochMilli(), "恢复", "#2f9e44"));
        }
        int chartNo = 0;
        for (Map<String, Object> m : trendMetrics) {
            String code = str(m.get("code"));
            if (!StringUtils.hasText(code)) continue;
            MetricTrendVo trend = metricQueryService.metricTrend(event.getInstanceId(), code, fromMs, toMs, "1m");
            List<List<Object>> points = trend.getPoints() == null ? List.of()
                    : trend.getPoints().stream().map(p -> List.<Object>of(p.getTs(), p.getValue())).toList();
            String label = StringUtils.hasText(str(m.get("label"))) ? str(m.get("label")) : code;
            sections.add(chartSection(
                    "二、关联指标趋势" + (trendMetrics.size() > 1 ? "（" + (++chartNo) + "）" : "") + "：" + label,
                    str(m.get("unit")),
                    List.of(Map.of("name", label, "points", points)),
                    markers,
                    "告警窗口内暂无该指标采集数据"));
        }
        if (trendMetrics.isEmpty()) {
            sections.add(listSection("二、关联指标趋势", List.of("该事件无可关联的数值指标趋势（如实例不可连接类事件）。")));
        }

        // 3~5. 画像：可能原因 / 排查路径 / 建议动作
        sections.add(listSection("三、初步原因分析", profileCauses(profile, scenario)));
        sections.add(listSection("四、排查路径", profileSteps(profile)));
        sections.add(buildActionSection("五、建议处理动作（仅供辅助决策，高风险动作须人工确认后执行）", profile));

        // 6. 处理记录（操作流水，含备注）
        List<AlertEventOperateLog> logs = operateLogMapper.selectList(new LambdaQueryWrapper<AlertEventOperateLog>()
                .eq(AlertEventOperateLog::getEventId, event.getId())
                .orderByAsc(AlertEventOperateLog::getId));
        List<Map<String, Object>> logRows = logs.stream().map(l -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", fmt(l.getCreatedAt()));
            row.put("operator", StringUtils.hasText(l.getOperatorName()) ? l.getOperatorName() : "系统");
            row.put("operate", operateLabel(l.getOperateType()));
            row.put("transition", str(l.getFromStatus()) + " → " + str(l.getToStatus()));
            row.put("remark", str(l.getRemark()));
            return row;
        }).toList();
        sections.add(tableSection("六、处理记录", List.of(
                col("time", "时间"), col("operator", "操作人"), col("operate", "操作"),
                col("transition", "状态流转"), col("remark", "处置备注")
        ), logRows, "尚无人工处置记录"));

        // 7. 恢复情况
        sections.add(summarySection("七、恢复情况",
                event.getRecoveryTime() != null
                        ? "事件已于 " + fmt(event.getRecoveryTime()) + " 恢复，持续 " + fmtDuration(durationSec) + "。"
                        : "截至报告生成时（" + fmt(now) + "）事件尚未恢复，已持续 " + fmtDuration(durationSec) + "，请持续跟进处理。",
                List.of(
                        kv("恢复时间", event.getRecoveryTime() != null ? fmt(event.getRecoveryTime()) : "未恢复"),
                        kv("触发轮次", String.valueOf(event.getTriggerCount() == null ? 1 : event.getTriggerCount())),
                        kv("最近触发", str(fmt(event.getLastTriggerTime())))
                )));

        // 8. 后续预防建议
        List<String> prevention = new ArrayList<>();
        if (profile != null && profile.getActions() != null) {
            profile.getActions().stream()
                    .filter(a -> "low".equals(str(a.get("risk"))))
                    .limit(3)
                    .forEach(a -> prevention.add(str(a.get("action")) + "：" + str(a.get("description"))));
        }
        if (rule != null) {
            prevention.add("若该告警属于业务正常波动，可在「告警规则管理」中调整规则「" + str(event.getRuleName()) + "」的阈值或持续轮次降噪。");
        }
        prevention.add("建议将本次处理经验沉淀到知识库，便于同类事件快速定位。");
        prevention.add("可在「报告中心」配置定期巡检报告，持续跟踪该实例的健康趋势。");
        sections.add(listSection("八、后续预防建议", prevention));
        return sections;
    }

    /** 事件趋势指标清单：画像 relatedMetrics（最多 4 个）优先，否则退回触发指标本身。 */
    private List<Map<String, Object>> resolveTrendMetrics(DrilldownProfileVo profile,
                                                          String metricCode, String metricLabel, String unit) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (profile != null && profile.getRelatedMetrics() != null) {
            for (Map<String, Object> m : profile.getRelatedMetrics()) {
                if (result.size() >= 4) break;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", str(m.get("code")));
                item.put("label", str(m.get("label")));
                item.put("unit", unitSymbol(str(m.get("unit"))));
                result.add(item);
            }
        }
        if (result.isEmpty() && StringUtils.hasText(metricCode) && !metricCode.startsWith("system.")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", metricCode);
            item.put("label", metricLabel);
            item.put("unit", unit);
            result.add(item);
        }
        return result;
    }

    private List<String> profileCauses(DrilldownProfileVo profile, boolean scenario) {
        if (profile == null || profile.getCauses() == null || profile.getCauses().isEmpty()) {
            return List.of("暂无匹配的原因画像，可在「系统设置 → 下钻画像」中补充。");
        }
        return profile.getCauses().stream().map(c -> {
            String head = str(c.get("cause"));
            if (!scenario && c.get("confidence") instanceof Number n) {
                head += "（可信度 " + Math.round(n.doubleValue() * 100) + "%）";
            }
            List<?> evidence = c.get("evidence") instanceof List<?> l ? l : List.of();
            String ev = evidence.stream().map(ReportServiceImpl::str).collect(Collectors.joining("；"));
            return StringUtils.hasText(ev) ? head + "：" + ev : head;
        }).toList();
    }

    private List<String> profileSteps(DrilldownProfileVo profile) {
        if (profile == null || profile.getSteps() == null || profile.getSteps().isEmpty()) {
            return List.of("暂无排查路径画像。");
        }
        return profile.getSteps().stream()
                .map(s -> str(s.get("title")) + "：" + str(s.get("description")))
                .toList();
    }

    private Map<String, Object> buildActionSection(String title, DrilldownProfileVo profile) {
        List<Map<String, Object>> rows = profile == null || profile.getActions() == null ? List.of()
                : profile.getActions().stream().map(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("action", str(a.get("action")));
                    row.put("risk", riskLabel(str(a.get("risk"))));
                    row.put("description", str(a.get("description")));
                    row.put("impact", str(a.get("impact")));
                    return row;
                }).toList();
        return tableSection(title, List.of(
                col("action", "建议动作"), col("risk", "风险"), col("description", "说明"), col("impact", "预期影响")
        ), rows, "暂无建议动作画像");
    }

    /** 系统内置连接失败事件判定（与 AlertEventServiceImpl 同口径）。 */
    private static boolean isConnectionFailureEvent(AlertEvent e) {
        return e.getRuleId() == null && StringUtils.hasText(e.getDedupKey())
                && e.getDedupKey().startsWith(Constants.SYSTEM_RULE_CONNECTION_FAILURE + ":");
    }

    private static String operateLabel(String t) {
        return switch (t == null ? "" : t) {
            case "confirm" -> "确认";
            case "handling" -> "受理";
            case "silence" -> "静默";
            case "close" -> "关闭";
            case "auto_recover" -> "自动恢复";
            default -> t;
        };
    }

    private static String riskLabel(String r) {
        return switch (r == null ? "" : r) {
            case "low" -> "低";
            case "medium" -> "中";
            case "high" -> "高";
            default -> r;
        };
    }

    private static String fmtDuration(long seconds) {
        if (seconds < 60) return seconds + " 秒";
        if (seconds < 3600) return (seconds / 60) + " 分钟";
        return String.format("%.1f 小时", seconds / 3600.0);
    }

    /** 指标单位符号化：percent→%、其余原样（与前端 fmtUnit 同口径）。 */
    private static String unitSymbol(String unit) {
        if (!StringUtils.hasText(unit)) return "";
        return switch (unit) {
            case "percent" -> "%";
            case "count", "int", "boolean" -> "";
            default -> unit;
        };
    }

    // ── 定时任务 ─────────────────────────────────────────────────────────────

    @Override
    public List<ReportScheduleVo> schedules() {
        return scheduleMapper.selectList(new LambdaQueryWrapper<ReportSchedule>()
                        .orderByDesc(ReportSchedule::getCreatedAt).orderByDesc(ReportSchedule::getId))
                .stream().map(this::toScheduleVo).toList();
    }

    private ReportScheduleVo toScheduleVo(ReportSchedule e) {
        ReportScheduleVo vo = new ReportScheduleVo();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setReportType(e.getReportType());
        vo.setCycle(e.getCycle());
        vo.setScopeType(e.getScopeType());
        vo.setScopeText(e.getScopeText());
        vo.setInstanceIds(e.getInstanceIds());
        vo.setTimeRange(e.getTimeRange());
        vo.setFrequency(e.getFrequency());
        vo.setRunTime(e.getRunTime());
        vo.setNotifyEmails(e.getNotifyEmails());
        vo.setNextRun(fmt(e.getNextRun()));
        vo.setLastRunTime(fmt(e.getLastRunTime()));
        vo.setEnabled(e.getEnabled());
        vo.setCreatedBy(e.getCreatedBy());
        return vo;
    }

    @Override
    public Long saveSchedule(ReportScheduleSaveRequest req) {
        ResolvedScope scope = resolveScope(req.getScopeType(), req.getInstanceIds(), req.getGroupIds(), req.getOwnerIds());
        DataScope ds = dataScopeService.currentScope();
        for (Long id : scope.instanceIds()) {
            if (!ds.allows(id)) {
                throw new BusinessException("无权访问实例: " + id);
            }
        }
        if (!List.of("daily", "weekly", "monthly").contains(req.getFrequency())) {
            throw new BusinessException("非法执行频率: " + req.getFrequency());
        }
        List<String> notifyEmails = normalizeEmails(req.getNotifyEmails());

        ReportSchedule e = req.getId() == null ? new ReportSchedule() : scheduleMapper.selectById(req.getId());
        if (e == null) {
            throw new BusinessException("定时任务不存在: " + req.getId());
        }
        e.setReportType(req.getReportType());
        e.setCycle("inspection".equals(req.getReportType()) ? req.getCycle() : null);
        e.setScopeType(scope.scopeType());
        e.setScopeText(scope.scopeText());
        e.setInstanceIds(scope.instanceIds());
        e.setTimeRange(normalizeRange(req.getTimeRange()));
        e.setFrequency(req.getFrequency());
        e.setRunTime(req.getRunTime());
        e.setNotifyEmails(notifyEmails);
        e.setName(dictLabel("report_type", req.getReportType())
                + (StringUtils.hasText(e.getCycle()) ? "·" + dictLabel("report_cycle", e.getCycle()) : "")
                + "（" + dictLabel("report_frequency", req.getFrequency()) + " " + req.getRunTime() + "）");
        e.setNextRun(computeNextRun(req.getFrequency(), req.getRunTime()));
        e.setUpdatedAt(OffsetDateTime.now());
        if (e.getId() == null) {
            e.setEnabled(true);
            e.setCreatedBy(currentUserName());
            e.setCreatedAt(OffsetDateTime.now());
            scheduleMapper.insert(e);
        } else {
            scheduleMapper.updateById(e);
        }
        return e.getId();
    }

    @Override
    public void toggleSchedule(Long id, boolean enabled) {
        ReportSchedule e = scheduleMapper.selectById(id);
        if (e == null) {
            throw new BusinessException("定时任务不存在: " + id);
        }
        e.setEnabled(enabled);
        if (enabled) {
            e.setNextRun(computeNextRun(e.getFrequency(), e.getRunTime()));
        }
        e.setUpdatedAt(OffsetDateTime.now());
        scheduleMapper.updateById(e);
    }

    @Override
    public void deleteSchedule(Long id) {
        scheduleMapper.deleteById(id);
    }

    @Override
    public Long runScheduleNow(Long id) {
        ReportSchedule e = scheduleMapper.selectById(id);
        if (e == null) {
            throw new BusinessException("定时任务不存在: " + id);
        }
        MonitorReport report = generateForSchedule(e);
        e.setLastRunTime(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        scheduleMapper.updateById(e);
        return report.getId();
    }

    @Override
    public int runDueSchedules() {
        List<ReportSchedule> due = scheduleMapper.selectList(new LambdaQueryWrapper<ReportSchedule>()
                .eq(ReportSchedule::getEnabled, true)
                .le(ReportSchedule::getNextRun, OffsetDateTime.now()));
        int generated = 0;
        for (ReportSchedule s : due) {
            try {
                generateForSchedule(s);
                generated++;
            } catch (Exception ex) {
                log.warn("定时报告生成失败 scheduleId={} name={}: {}", s.getId(), s.getName(), ex.getMessage());
            } finally {
                // 无论成败都推进 next_run，避免失败任务每轮重试造成堆积
                s.setLastRunTime(OffsetDateTime.now());
                s.setNextRun(computeNextRun(s.getFrequency(), s.getRunTime()));
                s.setUpdatedAt(OffsetDateTime.now());
                scheduleMapper.updateById(s);
            }
        }
        return generated;
    }

    private MonitorReport generateForSchedule(ReportSchedule s) {
        ResolvedScope scope = new ResolvedScope(s.getScopeType(), s.getScopeText(),
                s.getInstanceIds() == null ? List.of() : s.getInstanceIds());
        MonitorReport report = doGenerate(s.getReportType(), s.getCycle(), scope,
                normalizeRange(s.getTimeRange()), "schedule", "system");
        // 生成后邮件推送（§11.9 报告分发）：失败仅记日志，不影响归档主流程
        if (!CollectionUtils.isEmpty(s.getNotifyEmails())) {
            reportMailService.send(s.getNotifyEmails(), report);
        }
        return report;
    }

    /** 邮箱清洗：去空白、去重，格式非法的直接拒绝。 */
    private static List<String> normalizeEmails(List<String> emails) {
        if (CollectionUtils.isEmpty(emails)) {
            return List.of();
        }
        List<String> result = emails.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        for (String email : result) {
            if (!email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
                throw new BusinessException("邮箱格式不正确: " + email);
            }
        }
        return result;
    }

    /** 计算下次执行时间：daily=明天（含今天未到点）/ weekly=下个周一 / monthly=下月 1 日。 */
    private OffsetDateTime computeNextRun(String frequency, String runTime) {
        LocalTime time;
        try {
            time = LocalTime.parse(runTime == null || runTime.isBlank() ? "08:30" : runTime);
        } catch (Exception e) {
            time = LocalTime.of(8, 30);
        }
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime candidate = switch (frequency) {
            case "weekly" -> {
                ZonedDateTime c = now.with(DayOfWeek.MONDAY).with(time);
                yield c.isAfter(now) ? c : c.plusWeeks(1);
            }
            case "monthly" -> {
                ZonedDateTime c = now.withDayOfMonth(1).with(time);
                yield c.isAfter(now) ? c : c.plusMonths(1);
            }
            default -> {
                ZonedDateTime c = now.with(time);
                yield c.isAfter(now) ? c : c.plusDays(1);
            }
        };
        return candidate.toOffsetDateTime();
    }

    // ── 通用支撑 ─────────────────────────────────────────────────────────────

    private List<Map<String, Object>> queryTopSql(Long instanceId, OffsetDateTime from, OffsetDateTime to, int limit) {
        try {
            return topSqlMapper.selectDigestPage(instanceId,
                    Timestamp.from(from.toInstant()), Timestamp.from(to.toInstant()),
                    null, null, null, null, null,
                    "total_timer_wait DESC", limit, 0);
        } catch (Exception e) {
            log.warn("报告 TopSQL 查询失败 instanceId={}: {}", instanceId, e.getMessage());
            return List.of();
        }
    }

    /** 各实例活跃（未恢复）告警事件数。 */
    private Map<Long, Integer> countActiveAlerts(List<Long> instanceIds) {
        if (instanceIds.isEmpty()) {
            return Map.of();
        }
        // 活跃事件不受报告时间窗限制（体现"当前待处理"状态），窗口取一个足够大的历史范围
        OffsetDateTime now = OffsetDateTime.now();
        return statsMapper.selectAlertTopInstances(instanceIds,
                        Timestamp.from(now.minusYears(1).toInstant()), Timestamp.from(now.toInstant()), 1000)
                .stream()
                .filter(r -> num(r.get("active_cnt")) > 0)
                .collect(Collectors.toMap(
                        r -> ((Number) r.get("instance_id")).longValue(),
                        r -> (int) num(r.get("active_cnt")),
                        (a, b) -> a));
    }

    private String currentUserName() {
        CurrentUserHolder.Current current = CurrentUserHolder.get();
        if (current == null || current.userId() == null) {
            return "system";
        }
        SysUser u = userMapper.selectById(current.userId());
        if (u == null) {
            return "system";
        }
        return StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername();
    }

    private String dictLabel(String dictType, String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return dictLabels(dictType).getOrDefault(value, value);
    }

    private Map<String, String> dictLabels(String dictType) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, dictType))
                .stream()
                .collect(Collectors.toMap(SysDictItem::getItemValue, SysDictItem::getItemLabel, (a, b) -> a));
    }

    private static String normalizeRange(String range) {
        return Set.of("24h", "7d", "30d").contains(range) ? range : "24h";
    }

    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "normal" -> "正常";
            case "abnormal" -> "异常";
            case "paused" -> "已暂停";
            default -> status;
        };
    }

    // ---- section 构造 ----

    private static Map<String, Object> summarySection(String title, String summary, List<Map<String, Object>> kv) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("title", title);
        s.put("type", "summary");
        s.put("summary", summary);
        s.put("kv", kv);
        return s;
    }

    private static Map<String, Object> tableSection(String title, List<Map<String, Object>> columns,
                                                    List<Map<String, Object>> rows, String emptyText) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("title", title);
        s.put("type", "table");
        s.put("columns", columns);
        s.put("rows", rows);
        s.put("emptyText", emptyText);
        return s;
    }

    /**
     * 图表分段：预览页用 ECharts 渲染折线图，导出 Word 时由前端截图嵌入。
     * series 点为 [tsMillis, value]；markers 为竖向标记线（如告警触发/恢复时刻）。
     */
    private static Map<String, Object> chartSection(String title, String unit,
                                                    List<Map<String, Object>> series,
                                                    List<Map<String, Object>> markers,
                                                    String emptyText) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("title", title);
        s.put("type", "chart");
        s.put("unit", unit == null ? "" : unit);
        s.put("series", series);
        s.put("markers", markers == null ? List.of() : markers);
        s.put("emptyText", emptyText);
        return s;
    }

    private static Map<String, Object> marker(long ts, String label, String color) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ts", ts);
        m.put("label", label);
        m.put("color", color);
        return m;
    }

    private static Map<String, Object> listSection(String title, List<String> items) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("title", title);
        s.put("type", "list");
        s.put("items", items);
        return s;
    }

    private static Map<String, Object> kv(String label, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        return m;
    }

    private static Map<String, Object> col(String key, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        return m;
    }

    // ---- 格式化 ----

    private static String fmt(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ZONE).format(TIME_FMT);
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double statVal(Map<String, Map<String, Object>> stats, String code, String key) {
        Map<String, Object> s = stats.get(code);
        return s == null ? 0 : num(s.get(key));
    }

    /** 数值展示：保留 2 位小数并去掉尾零。 */
    private static String pct(double v) {
        String s = String.format("%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    private static String fmtVal(Double v, String unit) {
        if (v == null) {
            return "-";
        }
        return pct(v) + unit;
    }

    private static String gb(long bytes) {
        return pct(bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
