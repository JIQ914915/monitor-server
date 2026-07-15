package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.api.request.PgPlanCaptureRequest;
import com.lzzh.monitor.api.request.PgQueryAnalyticsRequest;
import com.lzzh.monitor.api.response.PgAdvisorVo;
import com.lzzh.monitor.api.response.PgObjectAnalysisVo;
import com.lzzh.monitor.api.response.PgPlanHistoryVo;
import com.lzzh.monitor.api.response.PgQueryAnalyticsVo;
import com.lzzh.monitor.api.response.PgSqlRegressionVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.mapper.PgDiagnosticMapper;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PostgreSqlPhase2ServiceImpl implements PostgreSqlPhase2Service {
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "calls", "calls", "avgExecTime", "avg_exec_time_ms", "avgExecTimeMs", "avg_exec_time_ms",
            "totalExecTime", "total_exec_time_ms", "totalExecTimeMs", "total_exec_time_ms", "planTime", "total_plan_time_ms",
            "tempWritten", "temp_written", "walBytes", "wal_bytes",
            "stddev", "stddev_exec_time_ms", "lastSeen", "last_seen");

    @Resource private PgDiagnosticMapper mapper;
    @Resource private InstanceService instanceService;
    @Resource private DataScopeService dataScopeService;
    @Resource private PostgreSqlPlanService planService;
    @Resource private PostgreSqlAdvisorService advisorService;

    @Override
    public List<PgQueryAnalyticsVo> queryAnalytics(PgQueryAnalyticsRequest request) {
        requireInstance(request.getInstanceId());
        OffsetDateTime to = request.getTo() == null ? OffsetDateTime.now() : request.getTo();
        OffsetDateTime from = request.getFrom() == null ? to.minusDays(7) : request.getFrom();
        String column = SORT_COLUMNS.getOrDefault(request.getSortBy(), "total_exec_time_ms");
        String direction = "asc".equalsIgnoreCase(request.getSortDirection()) ? "ASC" : "DESC";
        int limit = Math.min(500, Math.max(1, request.getLimit() == null ? 100 : request.getLimit()));
        return mapper.selectQueryAnalytics(request.getInstanceId(), timestamp(from), timestamp(to),
                        trim(request.getDatabase()), trim(request.getUser()), trim(request.getQueryId()),
                        column + " " + direction, limit)
                .stream().map(this::toAnalytics).toList();
    }

    @Override
    public List<PgSqlRegressionVo> regressions(Long instanceId) {
        requireInstance(instanceId);
        refreshRegressions(instanceId);
        return mapper.selectRegressionEvents(instanceId, 200).stream().map(this::toRegression).toList();
    }

    private void refreshRegressions(Long instanceId) {
        OffsetDateTime currentTo = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime currentFrom = currentTo.minusHours(2);
        OffsetDateTime baselineTo = currentFrom;
        OffsetDateTime baselineFrom = baselineTo.minusDays(28);
        List<Map<String, Object>> rows = mapper.selectRegressionCandidates(instanceId,
                timestamp(currentFrom), timestamp(currentTo), timestamp(baselineFrom), timestamp(baselineTo));
        for (Map<String, Object> row : rows) {
            Double baselineCalls = nullableDouble(row, "baseline_calls");
            if (baselineCalls == null) {
                if (number(row, "calls") >= 3) persistRegression(instanceId, row,
                        row.get("last_seen_before_baseline") == null ? "new_sql" : "reappeared_sql", 0,
                        number(row, "calls"), baselineFrom, baselineTo, currentFrom, currentTo);
                continue;
            }
            detectRatio(instanceId, row, "call_surge", "baseline_calls", "calls", 2.0, 20,
                    baselineFrom, baselineTo, currentFrom, currentTo);
            detectRatio(instanceId, row, "slower", "baseline_avg_exec_ms", "avg_exec_ms", 1.5, 20,
                    baselineFrom, baselineTo, currentFrom, currentTo);
            detectRatio(instanceId, row, "planning_growth", "baseline_avg_plan_ms", "avg_plan_ms", 2.0, 5,
                    baselineFrom, baselineTo, currentFrom, currentTo);
            detectRatio(instanceId, row, "temp_growth", "baseline_temp_written", "temp_written", 2.0, 64,
                    baselineFrom, baselineTo, currentFrom, currentTo);
            detectRatio(instanceId, row, "wal_amplification", "baseline_wal_bytes", "wal_bytes", 2.0, 1_048_576,
                    baselineFrom, baselineTo, currentFrom, currentTo);
            detectRatio(instanceId, row, "variance_growth", "baseline_stddev_exec_ms", "stddev_exec_ms", 2.0, 20,
                    baselineFrom, baselineTo, currentFrom, currentTo);
        }
    }

    private void detectRatio(Long instanceId, Map<String, Object> row, String type,
                             String baselineKey, String currentKey, double threshold, double floor,
                             OffsetDateTime baselineFrom, OffsetDateTime baselineTo,
                             OffsetDateTime currentFrom, OffsetDateTime currentTo) {
        Double baseline = nullableDouble(row, baselineKey);
        double current = number(row, currentKey);
        if (baseline == null || baseline <= 0 || current < floor || current / baseline < threshold) return;
        persistRegression(instanceId, row, type, baseline, current,
                baselineFrom, baselineTo, currentFrom, currentTo);
    }

    private void persistRegression(Long instanceId, Map<String, Object> row, String type,
                                   double baseline, double current,
                                   OffsetDateTime baselineFrom, OffsetDateTime baselineTo,
                                   OffsetDateTime currentFrom, OffsetDateTime currentTo) {
        Map<String, Object> event = new HashMap<>();
        event.put("instanceId", instanceId);
        event.put("database", text(row, "database_name"));
        event.put("queryId", text(row, "query_id"));
        event.put("queryText", text(row, "query_text"));
        event.put("type", type);
        double ratio = baseline <= 0 ? current : current / baseline;
        event.put("severity", ratio >= 3 ? "critical" : "warning");
        event.put("baselineFrom", timestamp(baselineFrom));
        event.put("baselineTo", timestamp(baselineTo));
        event.put("currentFrom", timestamp(currentFrom));
        event.put("currentTo", timestamp(currentTo));
        event.put("baselineValue", baseline);
        event.put("currentValue", current);
        event.put("changeRatio", ratio);
        try { event.put("evidenceJson", cn.hutool.json.JSONUtil.toJsonStr(row)); }
        catch (Exception e) { event.put("evidenceJson", "{}"); }
        mapper.upsertRegression(event);
    }

    @Override
    public PgPlanHistoryVo capturePlan(PgPlanCaptureRequest request) {
        requireInstance(request.getInstanceId());
        return planService.capture(requireInstance(request.getInstanceId()), request);
    }

    @Override
    public List<PgPlanHistoryVo> planHistory(Long instanceId, String database, String queryId) {
        requireInstance(instanceId);
        return planService.history(instanceId, database, queryId);
    }

    @Override
    public List<PgAdvisorVo> vacuumAdvisor(Long instanceId) {
        return advisorService.vacuum(requireInstance(instanceId));
    }

    @Override
    public List<PgAdvisorVo> indexAdvisor(Long instanceId) {
        return advisorService.index(requireInstance(instanceId));
    }

    @Override
    public List<PgObjectAnalysisVo> objects(Long instanceId) {
        return advisorService.objects(requireInstance(instanceId));
    }

    private com.lzzh.monitor.api.response.CollectTargetVo requireInstance(Long instanceId) {
        if (instanceId == null || !dataScopeService.currentScope().allows(instanceId)) {
            throw new BusinessException("无权访问该实例");
        }
        var target = instanceService.getCollectTarget(instanceId);
        if (target == null) throw new BusinessException("实例不存在");
        if (!"POSTGRESQL".equalsIgnoreCase(target.getDbType())) {
            throw new BusinessException("该功能仅支持 PostgreSQL 实例");
        }
        return target;
    }

    private PgQueryAnalyticsVo toAnalytics(Map<String, Object> row) {
        PgQueryAnalyticsVo vo = new PgQueryAnalyticsVo();
        vo.setDatabase(text(row, "database_name")); vo.setUser(text(row, "user_name"));
        vo.setQueryId(text(row, "query_id")); vo.setQueryText(text(row, "query_text"));
        vo.setCalls(longNumber(row, "calls")); vo.setTotalExecTimeMs(number(row, "total_exec_time_ms"));
        vo.setAvgExecTimeMs(number(row, "avg_exec_time_ms"));
        vo.setMinExecTimeMs(number(row, "min_exec_time_ms")); vo.setMaxExecTimeMs(number(row, "max_exec_time_ms"));
        vo.setStddevExecTimeMs(number(row, "stddev_exec_time_ms"));
        vo.setPlanCount(number(row, "plan_count")); vo.setTotalPlanTimeMs(number(row, "total_plan_time_ms"));
        vo.setAvgPlanTimeMs(vo.getPlanCount() > 0 ? vo.getTotalPlanTimeMs() / vo.getPlanCount() : 0);
        vo.setSharedHit(number(row, "shared_hit")); vo.setSharedRead(number(row, "shared_read"));
        vo.setSharedDirtied(number(row, "shared_dirtied")); vo.setSharedWritten(number(row, "shared_written"));
        vo.setLocalHit(number(row, "local_hit")); vo.setLocalRead(number(row, "local_read"));
        vo.setTempRead(number(row, "temp_read")); vo.setTempWritten(number(row, "temp_written"));
        vo.setBlockReadTimeMs(nullableDouble(row, "block_read_time_ms"));
        vo.setBlockWriteTimeMs(nullableDouble(row, "block_write_time_ms"));
        vo.setWalRecords(number(row, "wal_records")); vo.setWalFpi(number(row, "wal_fpi"));
        vo.setWalBytes(number(row, "wal_bytes")); vo.setJitFunctions(number(row, "jit_functions"));
        vo.setJitTimeMs(number(row, "jit_time_ms")); vo.setRows(longNumber(row, "rows"));
        vo.setFirstSeen(time(row, "first_seen")); vo.setLastSeen(time(row, "last_seen"));
        vo.setStatsReset(time(row, "stats_reset")); vo.setDeallocations(longNumber(row, "deallocations"));
        return vo;
    }

    private PgSqlRegressionVo toRegression(Map<String, Object> row) {
        PgSqlRegressionVo vo = new PgSqlRegressionVo();
        vo.setId(longNumber(row, "id")); vo.setDatabase(text(row, "database_name"));
        vo.setQueryId(text(row, "query_id")); vo.setQueryText(text(row, "query_text"));
        vo.setType(text(row, "regression_type")); vo.setSeverity(text(row, "severity"));
        vo.setBaselineFrom(time(row, "baseline_from")); vo.setBaselineTo(time(row, "baseline_to"));
        vo.setCurrentFrom(time(row, "current_from")); vo.setCurrentTo(time(row, "current_to"));
        vo.setBaselineValue(nullableDouble(row, "baseline_value"));
        vo.setCurrentValue(nullableDouble(row, "current_value")); vo.setChangeRatio(nullableDouble(row, "change_ratio"));
        vo.setEvidence(jsonMap(row.get("evidence"))); vo.setDetectedAt(time(row, "detected_at"));
        return vo;
    }

    private Map<String, Object> jsonMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        try { return cn.hutool.json.JSONUtil.parseObj(String.valueOf(value)).toBean(Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    private static Timestamp timestamp(OffsetDateTime value) {
        return Timestamp.from(value.toInstant());
    }
    private static String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key); return value == null ? null : String.valueOf(value);
    }
    private static double number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number n ? n.doubleValue() : value == null ? 0 : Double.parseDouble(value.toString());
    }
    private static long longNumber(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number n ? n.longValue() : value == null ? 0 : Long.parseLong(value.toString());
    }
    private static Double nullableDouble(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value instanceof Number n ? n.doubleValue() : Double.valueOf(value.toString());
    }
    private static OffsetDateTime time(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof OffsetDateTime t) return t;
        if (value instanceof Timestamp t) return t.toLocalDateTime().atOffset(OffsetDateTime.now().getOffset());
        return null;
    }
}