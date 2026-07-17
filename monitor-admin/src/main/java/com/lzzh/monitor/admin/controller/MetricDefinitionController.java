package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.MetricDefinitionListRequest;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.service.metric.MetricDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 指标定义元数据接口。 */
@Tag(name = "指标定义元数据", description = "指标口径注册表：单位/描述/值类型")
@RestController
@RequestMapping("/api/v1/metric-definitions")
public class MetricDefinitionController {

    @Resource
    private MetricDefinitionService metricDefinitionService;

    @Operation(summary = "指标定义列表")
    @PostMapping("/list")
    public Result<List<MetricDefinition>> list(
            @RequestBody(required = false) MetricDefinitionListRequest request) {
        if (request != null && request.getDbTypeId() != null) {
            return Result.ok(metricDefinitionService.listByDbTypeId(request.getDbTypeId()));
        }
        return Result.ok(metricDefinitionService.listAll());
    }

    @Operation(summary = "刷新指标定义缓存")
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        metricDefinitionService.refreshCache();
        return Result.ok();
    }
}
