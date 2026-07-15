package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.PgSessionActionRequest;
import com.lzzh.monitor.api.request.PgQueryAnalyticsRequest;
import com.lzzh.monitor.api.request.PgPlanCaptureRequest;
import com.lzzh.monitor.api.request.PgPlanHistoryRequest;
import com.lzzh.monitor.api.request.PgSessionQueryRequest;
import com.lzzh.monitor.api.response.PgBlockingNodeVo;
import com.lzzh.monitor.api.response.PgDatabaseVo;
import com.lzzh.monitor.api.response.PgSessionVo;
import com.lzzh.monitor.api.response.PgQueryAnalyticsVo;
import com.lzzh.monitor.api.response.PgSqlRegressionVo;
import com.lzzh.monitor.api.response.PgPlanHistoryVo;
import com.lzzh.monitor.api.response.PgAdvisorVo;
import com.lzzh.monitor.api.response.PgObjectAnalysisVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.postgresql.PostgreSqlDiagnosticService;
import com.lzzh.monitor.service.postgresql.PostgreSqlPhase2Service;
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

    @Operation(summary = "发现实例内数据库")
    @PostMapping("/databases")
    @RequiresPerm("pg_session:view")
    public Result<List<PgDatabaseVo>> databases(@Valid @RequestBody IdRequest request) {
        return Result.ok(diagnosticService.databases(request.getId()));
    }

    @Operation(summary = "查询实时客户端会话")
    @PostMapping("/sessions")
    @RequiresPerm("pg_session:view")
    public Result<List<PgSessionVo>> sessions(@Valid @RequestBody PgSessionQueryRequest request) {
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
    public Result<List<PgQueryAnalyticsVo>> queryAnalytics(@Valid @RequestBody PgQueryAnalyticsRequest request) {
        return Result.ok(phase2Service.queryAnalytics(request));
    }

    @Operation(summary = "刷新并查询 SQL 性能回退事件")
    @PostMapping("/sql-regressions")
    @RequiresPerm("pg_query:view")
    public Result<List<PgSqlRegressionVo>> regressions(@Valid @RequestBody IdRequest request) {
        return Result.ok(phase2Service.regressions(request.getId()));
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
    public Result<List<PgObjectAnalysisVo>> objects(@Valid @RequestBody IdRequest request) {
        return Result.ok(phase2Service.objects(request.getId()));
    }
}