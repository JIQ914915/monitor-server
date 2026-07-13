package com.lzzh.monitor.service.metric;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.SlowSqlDigestDetailRequest;
import com.lzzh.monitor.api.request.SlowSqlDigestPageRequest;
import com.lzzh.monitor.api.request.SlowSqlDigestTrendRequest;
import com.lzzh.monitor.api.request.SlowSqlOptimizeMarkRequest;
import com.lzzh.monitor.api.request.SlowSqlRecordPageRequest;
import com.lzzh.monitor.api.request.SlowSqlSamplePageRequest;
import com.lzzh.monitor.api.request.SlowSqlWindowRequest;
import com.lzzh.monitor.api.response.InstanceVo;
import com.lzzh.monitor.api.response.SlowSqlAlertVo;
import com.lzzh.monitor.api.response.SlowSqlDigestTrendVo;
import com.lzzh.monitor.api.response.SlowSqlDigestVo;
import com.lzzh.monitor.api.response.SlowSqlRecordVo;
import com.lzzh.monitor.api.response.SlowSqlSampleVo;
import com.lzzh.monitor.api.response.SlowSqlStatsVo;
import com.lzzh.monitor.api.response.SlowSqlWindowCompareVo;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertRuleMetricRef;
import com.lzzh.monitor.dao.entity.SlowSqlOptimizeMark;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleMetricRefMapper;
import com.lzzh.monitor.dao.mapper.SlowSqlOptimizeMarkMapper;
import com.lzzh.monitor.dao.ts.TsParamQueryDao;
import com.lzzh.monitor.dao.ts.TsSlowSqlSampleQueryDao;
import com.lzzh.monitor.dao.ts.TsTempTableStatsDao;
import com.lzzh.monitor.dao.ts.TsTopSqlQueryDao;
import com.lzzh.monitor.service.instance.InstanceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 慢 SQL 分析查询服务实现。
 * <p>耗时单位换算约定：metric_top_sql 中 delta_timer_wait 为皮秒、avg_timer_wait_us 为微秒，
 * 对外统一换算为毫秒（保留 2 位小数），前端不再感知皮秒/微秒。
 */
@Service
public class SlowSqlQueryServiceImpl implements SlowSqlQueryService {

    /** 默认查询窗口：最近 24 小时。 */
    private static final long DEFAULT_WINDOW_MS = 24L * 60 * 60 * 1000;

    /** 指纹趋势默认窗口：最近 7 天。 */
    private static final long DEFAULT_TREND_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;

    /** 皮秒 → 毫秒。 */
    private static final double PS_TO_MS = 1_000_000_000d;

    /** 微秒 → 毫秒。 */
    private static final double US_TO_MS = 1_000d;

    private static final String LONG_QUERY_TIME_CODE = "mysql.var.long_query_time";

    /** PG pg_stat_statements 扩展状态指标（0=未启用 1=差一步 2=就绪，天级探测）。 */
    private static final String PG_STAT_STATEMENTS_METRIC = "pg.ext.pg_stat_statements";

    /** 慢 SQL 相关告警规则的依赖指标编码。 */
    private static final List<String> SLOW_SQL_METRIC_CODES = List.of("mysql.delta.slow_queries");

    /** 未标记时的默认优化状态（字典 slow_sql_optimize_status）。 */
    private static final String OPTIMIZE_STATUS_DEFAULT = "unoptimized";

    @Resource
    private TsTopSqlQueryDao topSqlQueryDao;
    @Resource
    private TsSlowSqlSampleQueryDao slowSqlSampleQueryDao;
    @Resource
    private TsTempTableStatsDao tempTableStatsDao;
    @Resource
    private TsParamQueryDao paramQueryDao;
    @Resource
    private InstanceService instanceService;
    @Resource
    private SlowSqlOptimizeMarkMapper optimizeMarkMapper;
    @Resource
    private AlertEventMapper alertEventMapper;
    @Resource
    private AlertRuleMetricRefMapper alertRuleMetricRefMapper;

    @Override
    public PageResult<SlowSqlDigestVo> pageDigest(SlowSqlDigestPageRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);
        Long minAvgUs = request.getMinAvgMs() == null || request.getMinAvgMs() <= 0
                ? null : request.getMinAvgMs() * 1000;
        Long maxAvgUs = request.getMaxAvgMs() == null || request.getMaxAvgMs() <= 0
                ? null : request.getMaxAvgMs() * 1000;
        TsTopSqlQueryDao.DigestQuery query = new TsTopSqlQueryDao.DigestQuery(
                request.getInstanceId(), window[0], window[1],
                request.getKeyword(), request.getSchemaName(), request.getSqlType(),
                minAvgUs, maxAvgUs, request.getSortField(), Boolean.TRUE.equals(request.getAsc()),
                request.getPageNum() == null ? 1 : request.getPageNum(),
                request.getPageSize() == null ? 20 : request.getPageSize());

        long total = topSqlQueryDao.countDigest(query);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        List<SlowSqlDigestVo> rows = topSqlQueryDao.pageDigest(query).stream()
                .map(SlowSqlQueryServiceImpl::toDigestVo)
                .toList();
        fillOptimizeStatus(request.getInstanceId(), rows);
        return PageResult.of(rows, total);
    }

    @Override
    public PageResult<SlowSqlRecordVo> pageRecords(SlowSqlRecordPageRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);
        Long minAvgUs = request.getMinAvgMs() == null || request.getMinAvgMs() <= 0
                ? null : request.getMinAvgMs() * 1000;
        Long maxAvgUs = request.getMaxAvgMs() == null || request.getMaxAvgMs() <= 0
                ? null : request.getMaxAvgMs() * 1000;
        TsTopSqlQueryDao.RecordQuery query = new TsTopSqlQueryDao.RecordQuery(
                request.getInstanceId(), window[0], window[1],
                request.getSqlType(), minAvgUs, maxAvgUs,
                request.getDigest(), request.getSchemaName(),
                request.getPageNum() == null ? 1 : request.getPageNum(),
                request.getPageSize() == null ? 20 : request.getPageSize());

        long total = topSqlQueryDao.countRecords(query);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        List<SlowSqlRecordVo> rows = topSqlQueryDao.pageRecords(query).stream()
                .map(SlowSqlQueryServiceImpl::toRecordVo)
                .toList();
        return PageResult.of(rows, total);
    }

    @Override
    public SlowSqlDigestVo digestDetail(SlowSqlDigestDetailRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);
        TsTopSqlQueryDao.DigestRow row = topSqlQueryDao.getDigestDetail(
                request.getInstanceId(), request.getSchemaName(), request.getDigest(),
                window[0], window[1]);
        if (row == null) {
            return null;
        }
        SlowSqlDigestVo vo = toDigestVo(row);
        fillOptimizeStatus(request.getInstanceId(), List.of(vo));
        return vo;
    }

    @Override
    public PageResult<SlowSqlSampleVo> pageSamples(SlowSqlSamplePageRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);
        Long minExecUs = request.getMinExecMs() == null || request.getMinExecMs() <= 0
                ? null : request.getMinExecMs() * 1000;
        Long maxExecUs = request.getMaxExecMs() == null || request.getMaxExecMs() <= 0
                ? null : request.getMaxExecMs() * 1000;
        TsSlowSqlSampleQueryDao.SampleQuery query = new TsSlowSqlSampleQueryDao.SampleQuery(
                request.getInstanceId(), window[0], window[1],
                request.getSqlType(), minExecUs, maxExecUs, request.getDigest(),
                request.getSortField(), Boolean.TRUE.equals(request.getAsc()),
                request.getPageNum() == null ? 1 : request.getPageNum(),
                request.getPageSize() == null ? 20 : request.getPageSize());

        long total = slowSqlSampleQueryDao.countSamples(query);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        List<SlowSqlSampleVo> rows = slowSqlSampleQueryDao.pageSamples(query).stream()
                .map(SlowSqlQueryServiceImpl::toSampleVo)
                .toList();
        return PageResult.of(rows, total);
    }

    private static SlowSqlSampleVo toSampleVo(TsSlowSqlSampleQueryDao.SampleRow row) {
        SlowSqlSampleVo vo = new SlowSqlSampleVo();
        vo.setSampleKey(row.threadId() + "-" + row.eventId());
        vo.setConnUser(row.connUser());
        vo.setConnHost(row.connHost());
        vo.setSchemaName(row.schemaName());
        vo.setDigest(row.digest());
        vo.setSqlText(row.sqlText());
        vo.setSqlType(inferSqlType(row.sqlText()));
        vo.setExecTimeMs(usToMs(row.execTimeUs()));
        vo.setLockTimeMs(usToMs(row.lockTimeUs()));
        vo.setRowsExamined(row.rowsExamined());
        vo.setRowsSent(row.rowsSent());
        vo.setSortRows(row.sortRows());
        vo.setNoIndexUsed(row.noIndexUsed());
        vo.setTmpTables(row.tmpTables());
        vo.setTmpDiskTables(row.tmpDiskTables());
        vo.setCollectTime(row.collectTimeMillis());
        return vo;
    }

    @Override
    public void markOptimizeStatus(SlowSqlOptimizeMarkRequest request, String operator) {
        optimizeMarkMapper.upsertStatus(request.getInstanceId(), request.getSchemaName(),
                request.getDigest(), request.getStatus(), operator);
    }

    @Override
    public List<SlowSqlAlertVo> listSlowSqlAlerts(SlowSqlWindowRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);

        Set<Long> ruleIds = alertRuleMetricRefMapper.selectList(
                        new LambdaQueryWrapper<AlertRuleMetricRef>()
                                .in(AlertRuleMetricRef::getMetricCode, SLOW_SQL_METRIC_CODES))
                .stream().map(AlertRuleMetricRef::getRuleId).collect(Collectors.toSet());
        if (ruleIds.isEmpty()) {
            return List.of();
        }

        OffsetDateTime from = Instant.ofEpochMilli(window[0]).atOffset(ZoneOffset.UTC);
        OffsetDateTime to = Instant.ofEpochMilli(window[1]).atOffset(ZoneOffset.UTC);
        // 活跃期 [trigger_time, recovery_time] 与窗口有交叠即视为窗口内相关事件
        List<AlertEvent> events = alertEventMapper.selectList(
                new LambdaQueryWrapper<AlertEvent>()
                        .eq(AlertEvent::getInstanceId, request.getInstanceId())
                        .in(AlertEvent::getRuleId, ruleIds)
                        .le(AlertEvent::getTriggerTime, to)
                        .and(w -> w.isNull(AlertEvent::getRecoveryTime)
                                .or().ge(AlertEvent::getRecoveryTime, from))
                        .orderByDesc(AlertEvent::getTriggerTime)
                        .last("LIMIT 100"));
        return events.stream().map(SlowSqlQueryServiceImpl::toAlertVo).toList();
    }

    /** 批量补齐指纹行的优化状态（无标记记录默认 unoptimized）。 */
    private void fillOptimizeStatus(Long instanceId, List<SlowSqlDigestVo> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Set<String> digests = rows.stream().map(SlowSqlDigestVo::getDigest)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> statusByKey = new HashMap<>();
        if (!digests.isEmpty()) {
            List<SlowSqlOptimizeMark> marks = optimizeMarkMapper.selectList(
                    new LambdaQueryWrapper<SlowSqlOptimizeMark>()
                            .eq(SlowSqlOptimizeMark::getInstanceId, instanceId)
                            .in(SlowSqlOptimizeMark::getDigest, digests));
            for (SlowSqlOptimizeMark mark : marks) {
                statusByKey.put(markKey(mark.getSchemaName(), mark.getDigest()), mark.getStatus());
            }
        }
        for (SlowSqlDigestVo row : rows) {
            row.setOptimizeStatus(statusByKey.getOrDefault(
                    markKey(row.getSchemaName(), row.getDigest()), OPTIMIZE_STATUS_DEFAULT));
        }
    }

    private static String markKey(String schemaName, String digest) {
        return (schemaName == null ? "" : schemaName) + "|" + digest;
    }

    private static SlowSqlAlertVo toAlertVo(AlertEvent event) {
        SlowSqlAlertVo vo = new SlowSqlAlertVo();
        vo.setId(event.getId());
        vo.setEventCode(event.getEventCode());
        vo.setRuleName(event.getRuleName());
        vo.setRuleLevel(event.getRuleLevel());
        vo.setStatus(event.getStatus());
        vo.setTriggerValue(event.getTriggerValue());
        vo.setThresholdValue(event.getThresholdValue());
        vo.setAlertMessage(event.getAlertMessage());
        vo.setDimensionKey(event.getDimensionKey());
        vo.setTriggerTime(toMillis(event.getTriggerTime()));
        vo.setRecoveryTime(toMillis(event.getRecoveryTime()));
        return vo;
    }

    private static Long toMillis(OffsetDateTime time) {
        return time == null ? null : time.toInstant().toEpochMilli();
    }

    @Override
    public SlowSqlStatsVo stats(SlowSqlWindowRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);
        TsTopSqlQueryDao.WindowStats ws =
                topSqlQueryDao.queryWindowStats(request.getInstanceId(), window[0], window[1]);

        SlowSqlStatsVo vo = new SlowSqlStatsVo();
        vo.setInstanceId(request.getInstanceId());
        vo.setDigestCount(ws.digestCount());
        vo.setTotalExecCount(ws.totalExecCount());
        vo.setTotalTimeMs(psToMs(ws.totalTimerWaitPs()));
        vo.setMaxAvgTimeMs(usToMs(ws.maxAvgTimerWaitUs()));
        vo.setTotalRowsExamined(ws.totalRowsExamined());
        vo.setAvgTimeMs(ws.totalExecCount() > 0
                ? round2(psToMs(ws.totalTimerWaitPs()) / ws.totalExecCount()) : 0d);
        vo.setNoIndexDigestCount(ws.noIndexDigestCount());
        vo.setTmpTableDigestCount(ws.tmpTableDigestCount());
        vo.setSlowQueriesToday(tempTableStatsDao.queryTodayStats(request.getInstanceId()).slowQueriesToday());

        Map<String, Double> params = paramQueryDao.latestNumericParams(
                request.getInstanceId(), List.of(LONG_QUERY_TIME_CODE));
        vo.setLongQueryTimeSeconds(params.get(LONG_QUERY_TIME_CODE));

        vo.setTopSqlSupported(isTopSqlSupported(request.getInstanceId()));
        return vo;
    }

    @Override
    public SlowSqlDigestTrendVo digestTrend(SlowSqlDigestTrendRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_TREND_WINDOW_MS);
        List<TsTopSqlQueryDao.DigestTrendPoint> points = topSqlQueryDao.queryDigestTrend(
                request.getInstanceId(), request.getSchemaName(), request.getDigest(),
                window[0], window[1]);

        SlowSqlDigestTrendVo vo = new SlowSqlDigestTrendVo();
        vo.setInstanceId(request.getInstanceId());
        vo.setDigest(request.getDigest());
        vo.setPoints(points.stream().map(p -> {
            SlowSqlDigestTrendVo.Point point = new SlowSqlDigestTrendVo.Point();
            point.setTs(p.ts());
            point.setExecCount(p.execCount());
            point.setAvgTimeMs(usToMs(p.avgTimerWaitUs()));
            point.setRowsExamined(p.rowsExamined());
            return point;
        }).toList());
        return vo;
    }

    @Override
    public List<String> listSchemas(SlowSqlWindowRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);
        return topSqlQueryDao.listSchemaNames(request.getInstanceId(), window[0], window[1]);
    }

    /** 时段对比：当前窗口 Top 条数。 */
    private static final int COMPARE_TOP_N = 10;

    /** 时段对比：对比窗口的排名扫描深度（超出该名次视为"未上榜"）。 */
    private static final int COMPARE_RANK_SCAN_N = 50;

    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    @Override
    public SlowSqlWindowCompareVo windowCompare(SlowSqlWindowRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);
        Long instanceId = request.getInstanceId();
        long from = window[0];
        long to = window[1];

        SlowSqlWindowCompareVo vo = new SlowSqlWindowCompareVo();
        vo.setInstanceId(instanceId);
        vo.setCurrent(windowSummary(instanceId, from, to));
        vo.setYesterday(windowSummary(instanceId, from - ONE_DAY_MS, to - ONE_DAY_MS));
        vo.setLastWeek(windowSummary(instanceId, from - 7 * ONE_DAY_MS, to - 7 * ONE_DAY_MS));

        // 当前窗口 Top N（按总耗时降序）+ 两个对比窗口的排名/耗时映射
        List<TsTopSqlQueryDao.DigestRow> currentTop =
                queryTopByTotalTime(instanceId, from, to, COMPARE_TOP_N);
        Map<String, RankInfo> yesterdayRanks =
                rankMap(instanceId, from - ONE_DAY_MS, to - ONE_DAY_MS);
        Map<String, RankInfo> lastWeekRanks =
                rankMap(instanceId, from - 7 * ONE_DAY_MS, to - 7 * ONE_DAY_MS);

        List<SlowSqlWindowCompareVo.TopItem> items = new java.util.ArrayList<>(currentTop.size());
        for (int i = 0; i < currentTop.size(); i++) {
            TsTopSqlQueryDao.DigestRow row = currentTop.get(i);
            SlowSqlWindowCompareVo.TopItem item = new SlowSqlWindowCompareVo.TopItem();
            item.setSchemaName(row.schemaName());
            item.setDigest(row.digest());
            item.setDigestText(row.digestText());
            item.setSqlType(inferSqlType(row.digestText()));
            item.setRank(i + 1);
            item.setExecCount(row.execCount());
            item.setAvgTimeMs(usToMs(row.avgTimerWaitUs()));
            RankInfo y = yesterdayRanks.get(markKey(row.schemaName(), row.digest()));
            if (y != null) {
                item.setYesterdayRank(y.rank());
                item.setYesterdayAvgTimeMs(y.avgTimeMs());
            }
            RankInfo w = lastWeekRanks.get(markKey(row.schemaName(), row.digest()));
            if (w != null) {
                item.setLastWeekRank(w.rank());
                item.setLastWeekAvgTimeMs(w.avgTimeMs());
            }
            items.add(item);
        }
        vo.setTopItems(items);
        return vo;
    }

    private SlowSqlWindowCompareVo.WindowSummary windowSummary(Long instanceId, long from, long to) {
        TsTopSqlQueryDao.WindowStats ws = topSqlQueryDao.queryWindowStats(instanceId, from, to);
        SlowSqlWindowCompareVo.WindowSummary summary = new SlowSqlWindowCompareVo.WindowSummary();
        summary.setFrom(from);
        summary.setTo(to);
        summary.setDigestCount(ws.digestCount());
        summary.setTotalExecCount(ws.totalExecCount());
        summary.setAvgTimeMs(ws.totalExecCount() > 0
                ? round2(psToMs(ws.totalTimerWaitPs()) / ws.totalExecCount()) : null);
        summary.setMaxAvgTimeMs(ws.digestCount() > 0 ? usToMs(ws.maxAvgTimerWaitUs()) : null);
        return summary;
    }

    /** 查询窗口内按总耗时降序的 Top N 指纹。 */
    private List<TsTopSqlQueryDao.DigestRow> queryTopByTotalTime(Long instanceId, long from, long to, int topN) {
        TsTopSqlQueryDao.DigestQuery query = new TsTopSqlQueryDao.DigestQuery(
                instanceId, from, to, null, null, null, null, null,
                "totalTimerWait", false, 1, topN);
        return topSqlQueryDao.pageDigest(query);
    }

    /** 对比窗口的 (库|指纹) → 排名与平均耗时 映射（扫描前 COMPARE_RANK_SCAN_N 名）。 */
    private Map<String, RankInfo> rankMap(Long instanceId, long from, long to) {
        List<TsTopSqlQueryDao.DigestRow> rows =
                queryTopByTotalTime(instanceId, from, to, COMPARE_RANK_SCAN_N);
        Map<String, RankInfo> map = new HashMap<>(rows.size() * 2);
        for (int i = 0; i < rows.size(); i++) {
            TsTopSqlQueryDao.DigestRow row = rows.get(i);
            map.put(markKey(row.schemaName(), row.digest()),
                    new RankInfo(i + 1, usToMs(row.avgTimerWaitUs())));
        }
        return map;
    }

    /** 对比窗口内某指纹的排名与平均耗时。 */
    private record RankInfo(int rank, double avgTimeMs) {
    }

    // ── 指纹聚类（§15.4.6）───────────────────────────────────────────────────

    /** 聚类扫描的 digest 上限（按总耗时降序取前 N 个指纹参与聚类）。 */
    private static final int CLUSTER_DIGEST_SCAN_N = 500;

    /** 簇内保留的 Top 指纹明细条数。 */
    private static final int CLUSTER_TOP_DIGESTS = 5;

    @Override
    public PageResult<com.lzzh.monitor.api.response.SlowSqlClusterVo> clusters(
            com.lzzh.monitor.api.request.SlowSqlClusterPageRequest request) {
        long[] window = resolveWindow(request.getFrom(), request.getTo(), DEFAULT_WINDOW_MS);
        List<TsSlowSqlSampleQueryDao.DigestAggRow> rows = slowSqlSampleQueryDao.listDigestAggForCluster(
                request.getInstanceId(), window[0], window[1], CLUSTER_DIGEST_SCAN_N);
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), 0);
        }

        // 结构签名 = 语句类型 + 涉及表集合（字典序）；同签名的 digest 归入一簇
        Map<String, List<TsSlowSqlSampleQueryDao.DigestAggRow>> groups = new java.util.LinkedHashMap<>();
        Map<String, String> groupType = new HashMap<>();
        Map<String, List<String>> groupTables = new HashMap<>();
        for (TsSlowSqlSampleQueryDao.DigestAggRow row : rows) {
            String type = inferSqlType(row.sqlText());
            List<String> tables = extractTables(row.sqlText());
            String key = type + "@" + String.join(",", tables);
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(row);
            groupType.putIfAbsent(key, type);
            groupTables.putIfAbsent(key, tables);
        }

        List<com.lzzh.monitor.api.response.SlowSqlClusterVo> result = new java.util.ArrayList<>(groups.size());
        for (Map.Entry<String, List<TsSlowSqlSampleQueryDao.DigestAggRow>> e : groups.entrySet()) {
            List<TsSlowSqlSampleQueryDao.DigestAggRow> members = e.getValue();
            members.sort(java.util.Comparator.comparingLong(
                    TsSlowSqlSampleQueryDao.DigestAggRow::totalUs).reversed());

            long sampleCount = 0, totalUs = 0, maxUs = 0;
            for (TsSlowSqlSampleQueryDao.DigestAggRow m : members) {
                sampleCount += m.sampleCount();
                totalUs += m.totalUs();
                maxUs = Math.max(maxUs, m.maxUs());
            }

            com.lzzh.monitor.api.response.SlowSqlClusterVo vo =
                    new com.lzzh.monitor.api.response.SlowSqlClusterVo();
            vo.setClusterKey(e.getKey());
            vo.setStatementType(groupType.get(e.getKey()));
            vo.setTables(groupTables.get(e.getKey()));
            vo.setDigestCount(members.size());
            vo.setSampleCount(sampleCount);
            vo.setTotalTimeMs(usToMs(totalUs));
            vo.setAvgTimeMs(sampleCount > 0 ? round2(totalUs / US_TO_MS / sampleCount) : null);
            vo.setMaxTimeMs(usToMs(maxUs));
            vo.setSampleSql(members.get(0).sqlText());
            vo.setSchemaName(members.get(0).schemaName());

            List<com.lzzh.monitor.api.response.SlowSqlClusterVo.ClusterDigest> digests =
                    new java.util.ArrayList<>();
            for (int i = 0; i < Math.min(members.size(), CLUSTER_TOP_DIGESTS); i++) {
                TsSlowSqlSampleQueryDao.DigestAggRow m = members.get(i);
                com.lzzh.monitor.api.response.SlowSqlClusterVo.ClusterDigest d =
                        new com.lzzh.monitor.api.response.SlowSqlClusterVo.ClusterDigest();
                d.setDigest(m.digest());
                d.setSampleCount(m.sampleCount());
                d.setTotalTimeMs(usToMs(m.totalUs()));
                d.setAvgTimeMs(usToMs(m.avgUs()));
                digests.add(d);
            }
            vo.setDigests(digests);
            result.add(vo);
        }
        result.sort(java.util.Comparator.comparingDouble(
                (com.lzzh.monitor.api.response.SlowSqlClusterVo v) ->
                        v.getTotalTimeMs() == null ? 0 : v.getTotalTimeMs()).reversed());

        // 聚簇必须先做全量分组（簇的边界与排序依赖全部成员），完成后按页切片返回
        int pageNum = request.getPageNum() == null || request.getPageNum() < 1 ? 1 : request.getPageNum();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 10
                : Math.min(request.getPageSize(), 100);
        int fromIdx = (pageNum - 1) * pageSize;
        List<com.lzzh.monitor.api.response.SlowSqlClusterVo> page = fromIdx >= result.size()
                ? List.of()
                : result.subList(fromIdx, Math.min(fromIdx + pageSize, result.size()));
        return PageResult.of(List.copyOf(page), result.size());
    }

    /** 提取 FROM / JOIN / UPDATE / INTO / DELETE FROM 后的表名（去库名前缀与反引号，字典序去重）。 */
    private static final java.util.regex.Pattern TABLE_REF_PATTERN = java.util.regex.Pattern.compile(
            "\\b(?:FROM|JOIN|INTO|UPDATE)\\s+(`?[\\w$]+`?(?:\\s*\\.\\s*`?[\\w$]+`?)?)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static List<String> extractTables(String sqlText) {
        if (sqlText == null || sqlText.isBlank()) {
            return List.of("(unknown)");
        }
        java.util.TreeSet<String> tables = new java.util.TreeSet<>();
        java.util.regex.Matcher m = TABLE_REF_PATTERN.matcher(sqlText);
        while (m.find()) {
            String ref = m.group(1).replace("`", "").replaceAll("\\s", "");
            int dot = ref.lastIndexOf('.');
            String table = (dot >= 0 ? ref.substring(dot + 1) : ref).toLowerCase(Locale.ROOT);
            // 排除子查询别名误匹配的关键字
            if (!table.isBlank() && !table.equals("select") && !table.equals("dual")) {
                tables.add(table);
            }
        }
        return tables.isEmpty() ? List.of("(unknown)") : List.copyOf(tables);
    }

    // ── 内部工具 ─────────────────────────────────────────────────────────────

    private static SlowSqlDigestVo toDigestVo(TsTopSqlQueryDao.DigestRow row) {
        SlowSqlDigestVo vo = new SlowSqlDigestVo();
        vo.setSchemaName(row.schemaName());
        vo.setDigest(row.digest());
        vo.setDigestText(row.digestText());
        vo.setSqlType(inferSqlType(row.digestText()));
        vo.setExecCount(row.execCount());
        vo.setTotalTimeMs(psToMs(row.totalTimerWaitPs()));
        vo.setAvgTimeMs(usToMs(row.avgTimerWaitUs()));
        vo.setMaxAvgTimeMs(usToMs(row.maxAvgTimerWaitUs()));
        vo.setRowsExamined(row.rowsExamined());
        vo.setRowsSent(row.rowsSent());
        vo.setScanRatio(row.rowsSent() > 0
                ? round2((double) row.rowsExamined() / row.rowsSent()) : null);
        vo.setLockTimeMs(psToMs(row.lockTimePs()));
        vo.setSortRows(row.sortRows());
        vo.setNoIndexUsed(row.noIndexUsed());
        vo.setTmpTables(row.tmpTables() + row.tmpDiskTables());
        vo.setTmpDiskTables(row.tmpDiskTables());
        vo.setFirstSeen(row.firstSeenMillis());
        vo.setLastSeen(row.lastSeenMillis());
        return vo;
    }

    private static SlowSqlRecordVo toRecordVo(TsTopSqlQueryDao.RecordRow row) {
        SlowSqlRecordVo vo = new SlowSqlRecordVo();
        vo.setCollectTime(row.collectTimeMillis());
        vo.setSchemaName(row.schemaName());
        vo.setDigest(row.digest());
        vo.setDigestText(row.digestText());
        vo.setSqlType(inferSqlType(row.digestText()));
        vo.setExecCount(row.execCount());
        vo.setAvgTimeMs(usToMs(row.avgTimerWaitUs()));
        vo.setTotalTimeMs(psToMs(row.totalTimerWaitPs()));
        vo.setRowsExamined(row.rowsExamined());
        vo.setRowsSent(row.rowsSent());
        vo.setLockTimeMs(psToMs(row.lockTimePs()));
        vo.setSortRows(row.sortRows());
        vo.setNoIndexUsed(row.noIndexUsed());
        vo.setTmpTables(row.tmpTables() + row.tmpDiskTables());
        vo.setTmpDiskTables(row.tmpDiskTables());
        return vo;
    }

    /** 由归一化 SQL 文本前缀推断 SQL 类型（digest_text 已由 P_S 统一为大写关键字开头）。 */
    private static String inferSqlType(String digestText) {
        if (digestText == null || digestText.isBlank()) {
            return "OTHER";
        }
        String upper = digestText.stripLeading().toUpperCase(Locale.ROOT);
        for (String type : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"}) {
            if (upper.startsWith(type)) {
                return type;
            }
        }
        return "OTHER";
    }

    /**
     * Top SQL 支持性按库类型判定：MySQL 5.6 无 P_S digest 采集；
     * PostgreSQL 依赖 pg_stat_statements 扩展（天级探测指标，2=就绪）。
     */
    private boolean isTopSqlSupported(Long instanceId) {
        try {
            InstanceVo instance = instanceService.getById(instanceId);
            if (instance == null || instance.getDbType() == null || instance.getDbType().isBlank()) {
                return false;
            }
            if ("PostgreSQL".equalsIgnoreCase(instance.getDbType())) {
                Double ext = paramQueryDao.latestNumericParams(instanceId,
                        List.of(PG_STAT_STATEMENTS_METRIC)).get(PG_STAT_STATEMENTS_METRIC);
                return ext != null && ext >= 2;
            }
            if (!"MySQL".equalsIgnoreCase(instance.getDbType())) {
                return false;
            }
            String version = instance.getDbVersion();
            return version == null || !version.startsWith("5.6");
        } catch (Exception e) {
            // 类型或实例信息查询失败时按不支持处理，禁止静默套用 MySQL 能力。
            return false;
        }
    }

    private static long[] resolveWindow(Long from, Long to, long defaultWindowMs) {
        long effectiveTo = to == null || to <= 0 ? System.currentTimeMillis() : to;
        long effectiveFrom = from == null || from <= 0 ? effectiveTo - defaultWindowMs : from;
        if (effectiveFrom > effectiveTo) {
            effectiveFrom = effectiveTo - defaultWindowMs;
        }
        return new long[]{effectiveFrom, effectiveTo};
    }

    private static double psToMs(long picoseconds) {
        return round2(picoseconds / PS_TO_MS);
    }

    private static double usToMs(long microseconds) {
        return round2(microseconds / US_TO_MS);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100d;
    }
}
