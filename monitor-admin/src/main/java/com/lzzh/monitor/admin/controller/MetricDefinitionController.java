package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.service.metric.MetricDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 指标定义元数据接口（P1-2）。
 * <p>前端通过此接口获取指标口径（单位/描述/值类型），驱动图表展示与 Tooltip；
 * 采集写入与健康评分等后端模块通过 MetricDefinitionService 内部调用，不走此接口。
 */
@Tag(name = "指标定义元数据", description = "指标口径注册表：单位/描述/值类型，供前端展示路由与 Tooltip 渲染")
@RestController
@RequestMapping("/api/v1/metric-definitions")
public class MetricDefinitionController {

    @Resource
    private MetricDefinitionService metricDefinitionService;

    /**
     * 查询全部指标定义（可按 dbType 过滤）。前端首次加载时拉取并缓存到本地，
     * 后续按 metricCode 快速查找单位、描述等口径信息。
     *
     * @param dbType 数据库类型（如 mysql，可空；留空返回全部）
     * @return 指标定义列表
     */
    @Operation(summary = "指标定义列表", description = "返回指标元数据（metricCode/unit/description/valueType），供前端渲染口径")
    @GetMapping
    public Result<List<MetricDefinition>> list(@RequestParam(required = false) String dbType) {
        if (dbType != null && !dbType.isBlank()) {
            return Result.ok(metricDefinitionService.listByDbType(dbType));
        }
        return Result.ok(metricDefinitionService.listAll());
    }

    /**
     * 刷新指标定义内存缓存（新增/修改指标定义后手动触发）。
     *
     * @return 空响应体
     */
    @Operation(summary = "刷新指标定义缓存", description = "热更新 MetricDefinition 内存缓存，无需重启服务")
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        metricDefinitionService.refreshCache();
        return Result.ok();
    }
}
