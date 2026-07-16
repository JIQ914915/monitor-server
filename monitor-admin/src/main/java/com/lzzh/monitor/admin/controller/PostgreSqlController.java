package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.PgPageRequest;
import com.lzzh.monitor.api.request.PgSessionActionRequest;
import com.lzzh.monitor.api.request.PgQueryAnalyticsRequest;
import com.lzzh.monitor.api.request.PgPlanCaptureRequest;
import com.lzzh.monitor.api.request.PgPlanHistoryRequest;
import com.lzzh.monitor.api.request.PgSessionQueryRequest;
import com.lzzh.monitor.api.request.PgOperationalEventQuery;
import com.lzzh.monitor.api.request.PgRestoreDrillRequest;
import com.lzzh.monitor.api.response.PgBlockingNodeVo;
import com.lzzh.monitor.api.response.PgDatabaseVo;
import com.lzzh.monitor.api.response.PgSessionVo;
import com.lzzh.monitor.api.response.PgQueryAnalyticsVo;
import com.lzzh.monitor.api.response.PgSqlRegressionVo;
import com.lzzh.monitor.api.response.PgPlanHistoryVo;
import com.lzzh.monitor.api.response.PgAdvisorVo;
import com.lzzh.monitor.api.response.PgObjectAnalysisVo;
import com.lzzh.monitor.api.response.PgOperationalEventVo;
import com.lzzh.monitor.api.response.PgOperationalHealthVo;
import com.lzzh.monitor.api.response.PgOperationalSummaryVo;
import com.lzzh.monitor.api.response.PgRestoreDrillVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.service.postgresql.PostgreSqlDiagnosticService;
import com.lzzh.monitor.service.postgresql.PostgreSqlPhase2Service;
import com.lzzh.monitor.service.postgresql.PostgreSqlPhase3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PostgreSQL 实时诊断")
@RestController
@RequestMapping("/api/v1/postgresql")
public class PostgreSqlController {
    @Resource private PostgreSqlDiagnosticService diagnosticService;
    @Resource private PostgreSqlPhase2Service phase2Service;
    @Resource private PostgreSqlPhase3Service phase3Service;

    @Operation(summary = "发现实例内数据库")
    @PostMapping("/databases")
    @RequiresPerm("pg_session:view")
    public Result<List<PgDatabaseVo>> databases(@Valid @RequestBody IdRequest request) {
        return Result.ok(diagnosticService.databases(request.getId()));
    }

    @Operation(summary = "查询实时客户端会话")
    @PostMapping("/sessions")
    @RequiresPerm("pg_session:view")
    public Result<PageResult<PgSessionVo>> sessions(@Valid @RequestBody PgSessionQueryRequest request) {
        return Result.ok(diagnosticService.sessions(request));
    }

    @Operation(summary = "查询实时阻塞树")
    @PostMapping("/blocking-tree")
    @RequiresPerm("pg_session:view")
    public Result<List<PgBlockingNodeVo>> blockingTree(@Valid @RequestBody IdRequest request) {
        return Result.ok(diagnosticService.blockingTree(request.getId()));
    }

    @Operation(summary = "取消会话当前查询")
    @PostMapping("/sessions/cancel")
    @RequiresPerm("pg_session:cancel")
    @OperateLog(module = "PostgreSQL 会话", action = "取消查询")
    public Result<Boolean> cancel(@Valid @RequestBody PgSessionActionRequest request) {
        return Result.ok(diagnosticService.cancel(request));
    }

    @Operation(summary = "终止客户端会话")
    @PostMapping("/sessions/terminate")
    @RequiresPerm("pg_session:terminate")
    @OperateLog(module = "PostgreSQL 会话", action = "终止会话")
    public Result<Boolean> terminate(@Valid @RequestBody PgSessionActionRequest request) {
        return Result.ok(diagnosticService.terminate(request));
    }
    @Operation(summary = "查询 PostgreSQL Query Analytics")
    @PostMapping("/query-analytics")
    @RequiresPerm("pg_query:view")
    public Result<PageResult<PgQueryAnalyticsVo>> queryAnalytics(@Valid @RequestBody PgQueryAnalyticsRequest request) {
        return Result.ok(phase2Service.queryAnalytics(request));
    }

    @Operation(summary = "刷新并查询 SQL 性能回退事件")
    @PostMapping("/sql-regressions")
    @RequiresPerm("pg_query:view")
    public Result<PageResult<PgSqlRegressionVo>> regressions(@Valid @RequestBody PgPageRequest request) {
        return Result.ok(phase2Service.regressions(request));
    }

    @Operation(summary = "安全采集 JSON 执行计划（不执行 ANALYZE）")
    @PostMapping("/plans/capture")
    @RequiresPerm("pg_plan:capture")
    @OperateLog(module = "PostgreSQL 执行计划", action = "安全 EXPLAIN")
    public Result<PgPlanHistoryVo> capturePlan(@Valid @RequestBody PgPlanCaptureRequest request) {
        return Result.ok(phase2Service.capturePlan(request));
    }

    @Operation(summary = "查询执行计划历史")
    @PostMapping("/plans/history")
    @RequiresPerm("pg_plan:view")
    public Result<List<PgPlanHistoryVo>> planHistory(@Valid @RequestBody PgPlanHistoryRequest request) {
        return Result.ok(phase2Service.planHistory(
                request.getInstanceId(), request.getDatabase(), request.getQueryId()));
    }

    @Operation(summary = "Vacuum Advisor")
    @PostMapping("/vacuum-advisor")
    @RequiresPerm("pg_advisor:view")
    public Result<List<PgAdvisorVo>> vacuumAdvisor(@Valid @RequestBody IdRequest request) {
        return Result.ok(phase2Service.vacuumAdvisor(request.getId()));
    }

    @Operation(summary = "Index Advisor")
    @PostMapping("/index-advisor")
    @RequiresPerm("pg_advisor:view")
    public Result<List<PgAdvisorVo>> indexAdvisor(@Valid @RequestBody IdRequest request) {
        return Result.ok(phase2Service.indexAdvisor(request.getId()));
    }

    @Operation(summary = "跨数据库对象与容量分析")
    @PostMapping("/objects")
    @RequiresPerm("pg_advisor:view")
    public Result<PageResult<PgObjectAnalysisVo>> objects(@Valid @RequestBody PgPageRequest request) {
        return Result.ok(phase2Service.objects(request));
    }
@Operation(summary = "逻辑和物理复制运维快照")
    @PostMapping("/operations/replication")
    @RequiresPerm("pg_replication:logical")
    public Result<PageResult<PgOperationalEventVo>> replicationOperations(@Valid @RequestBody PgOperationalEventQuery request) {
        String category = "replication".equals(request.getCategory()) ? "replication" : "logical_replication";
        return Result.ok(phase3Service.events(request, "postgresql", category, true));
    }
@Operation(summary = "WAL 归档状态")
    @PostMapping("/operations/backups")
    @RequiresPerm("pg_backup:view")
    public Result<PageResult<PgOperationalEventVo>> backups(@Valid @RequestBody PgOperationalEventQuery request) {
        return Result.ok(phase3Service.events(request, null, "backup", true));
    }

    @Operation(summary = "运维任务进度")
    @PostMapping("/operations/progress")
    @RequiresPerm("pg_progress:view")
    public Result<PageResult<PgOperationalEventVo>> progress(@Valid @RequestBody PgOperationalEventQuery request) {
        return Result.ok(phase3Service.events(request, "postgresql", "progress", true));
    }

    @Operation(summary = "统一运维事件时间线")
    @PostMapping("/operations/timeline")
    @RequiresPerm("pg_log:view")
    public Result<PageResult<PgOperationalEventVo>> timeline(@Valid @RequestBody PgOperationalEventQuery request) {
        return Result.ok(phase3Service.events(request, null, null, true));
    }

    @Operation(summary = "PostgreSQL 实例运维健康结论")
    @PostMapping("/operations/health")
    @RequiresPerm("pg_log:view")
    public Result<PgOperationalHealthVo> operationsHealth(@Valid @RequestBody IdRequest request) {
        return Result.ok(phase3Service.health(request.getId()));
    }

    @Operation(summary = "运维事件 24 小时聚合")
    @PostMapping("/operations/summary")
    @RequiresPerm("pg_log:view")
    public Result<List<PgOperationalSummaryVo>> operationsSummary(@Valid @RequestBody IdRequest request) {
        return Result.ok(phase3Service.summary(request.getId()));
    }

    @Operation(summary = "恢复演练记录")
    @PostMapping("/restore-drills")
    @RequiresPerm("pg_backup:view")
    public Result<PageResult<PgRestoreDrillVo>> restoreDrills(@Valid @RequestBody PgPageRequest request) {
        return Result.ok(phase3Service.restoreDrills(request));
    }

    @Operation(summary = "登记恢复演练")
    @PostMapping("/restore-drills/save")
    @RequiresPerm("pg_restore_drill:manage")
    @OperateLog(module = "PostgreSQL 归档与恢复", action = "登记恢复演练")
    public Result<PgRestoreDrillVo> saveRestoreDrill(@Valid @RequestBody PgRestoreDrillRequest request) {
        return Result.ok(phase3Service.saveRestoreDrill(request));
    }}