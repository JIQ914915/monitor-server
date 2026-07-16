package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.MetricInstanceRequest;
import com.lzzh.monitor.api.request.MySqlConfigDriftRequest;
import com.lzzh.monitor.api.request.MySqlCorrelationRequest;
import com.lzzh.monitor.api.request.MySqlSecurityBaselineRequest;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.mysql.MySqlDiagnosticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "MySQL P0/P1 诊断", description = "容量、配置、复制、MDL、等待关联与分级安全基线")
@RestController
@RequestMapping("/api/v1/mysql-diagnostics")
public class MySqlDiagnosticController {
    @Resource private MySqlDiagnosticService service;

    @Operation(summary = "容量、自增ID与Binlog风险")
    @PostMapping("/capacity-risks")
    public Result<Map<String,Object>> capacityRisks(@Valid @RequestBody MetricInstanceRequest request) {
        return Result.ok(service.capacityRisks(request.getInstanceId()));
    }

    @Operation(summary = "配置漂移、跨实例比较与风险巡检")
    @PostMapping("/config-drift")
    public Result<Map<String,Object>> configDrift(@Valid @RequestBody MySqlConfigDriftRequest request) {
        return Result.ok(service.configDrift(request));
    }

    @Operation(summary = "普通异步复制深度诊断")
    @PostMapping("/replication")
    public Result<Map<String,Object>> replication(@Valid @RequestBody MetricInstanceRequest request) {
        return Result.ok(service.replication(request.getInstanceId()));
    }

    @Operation(summary = "Metadata Lock 阻塞诊断")
    @PostMapping("/metadata-locks")
    public Result<Map<String,Object>> metadataLocks(@Valid @RequestBody MetricInstanceRequest request) {
        return Result.ok(service.metadataLocks(request.getInstanceId()));
    }

    @Operation(summary = "等待、Top SQL 与主机指标关联")
    @PostMapping("/correlation")
    public Result<Map<String,Object>> correlation(@Valid @RequestBody MySqlCorrelationRequest request) {
        return Result.ok(service.correlation(request));
    }

    @Operation(summary = "基础或增强安全配置基线")
    @PostMapping("/security-baseline")
    public Result<Map<String,Object>> security(@Valid @RequestBody MySqlSecurityBaselineRequest request) {
        return Result.ok(service.securityBaseline(request));
    }
}
