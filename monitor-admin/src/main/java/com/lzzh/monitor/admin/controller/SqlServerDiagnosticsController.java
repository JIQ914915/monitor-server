package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.MetricInstanceRequest;
import com.lzzh.monitor.api.response.SqlServerDiagnosticsVo;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.sqlserver.SqlServerDiagnosticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SQL Server 诊断")
@RestController
@RequestMapping("/api/sqlserver/diagnostics")
public class SqlServerDiagnosticsController {
    private final SqlServerDiagnosticsService diagnosticsService;

    public SqlServerDiagnosticsController(SqlServerDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @Operation(summary = "SQL Server 结构化诊断明细")
    @PostMapping("/overview")
    @RequiresPerm("realtime:view")
    public Result<SqlServerDiagnosticsVo> overview(@Valid @RequestBody MetricInstanceRequest request) {
        return Result.ok(diagnosticsService.overview(request.getInstanceId()));
    }
}