package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.CollectLogQueryRequest;
import com.lzzh.monitor.api.request.CollectTaskQueryRequest;
import com.lzzh.monitor.api.response.InstanceVo;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.dao.entity.CollectLog;
import com.lzzh.monitor.dao.mapper.CollectLogMapper;
import com.lzzh.monitor.service.instance.InstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采集日志接口：支撑采集器管理页面的任务列表与历史日志弹窗。
 *
 * <p>采集控制粒度只到实例层（status=paused 跳过）；本接口只提供只读的运行结果数据。
 */
@Tag(name = "采集日志", description = "采集运行结果查询：任务列表 + 历史日志")
@RestController
@RequestMapping("/api/v1/collect-logs")
public class CollectLogController {

    @Resource
    private CollectLogMapper collectLogMapper;
    @Resource
    private InstanceService instanceService;

    /**
     * 采集任务列表：每个实例 × 频率组合一行，展示最近执行状态与 24h 成功率。
     * 前端采集器管理页面主表格数据来源。
     */
    @Operation(summary = "采集任务列表", description = "返回每个实例×频率的最新采集状态，供采集器管理页面主表展示")
    @PostMapping("/tasks")
    public Result<List<Map<String, Object>>> tasks(@RequestBody(required = false) CollectTaskQueryRequest req) {
        String dbType = req == null ? null : req.getDbType();
        String frequency = req == null ? null : req.getFrequency();
        String status = req == null ? null : req.getStatus();

        // 1. 查实例基本信息（含 dbType/dbVersion 等 enrich 字段，用于展示与过滤）
        List<InstanceVo> instances = instanceService.listAll();
        Map<Long, InstanceVo> instanceMap = instances.stream()
                .collect(Collectors.toMap(InstanceVo::getId, i -> i));

        // 2. 查最新采集结果（DISTINCT ON instance_id, frequency）
        List<Map<String, Object>> latestRows = collectLogMapper.selectLatestPerTask();

        // 3. 组装：合并实例信息 + 计算状态 + 前端所需字段
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : latestRows) {
            Long instanceId = toLong(row.get("instance_id"));
            InstanceVo ins = instanceMap.get(instanceId);
            if (ins == null) continue;

            // dbType 过滤
            if (dbType != null && !dbType.isBlank()
                    && !dbType.equalsIgnoreCase(ins.getDbType())) {
                continue;
            }
            String freq = String.valueOf(row.get("frequency"));
            // frequency 过滤
            if (frequency != null && !frequency.isBlank() && !frequency.equals(freq)) {
                continue;
            }

            // 状态计算：paused 实例 → stopped；最近采集失败 → error；否则 running
            String taskStatus;
            if ("paused".equalsIgnoreCase(ins.getStatus())) {
                taskStatus = "stopped";
            } else if (Boolean.FALSE.equals(row.get("last_success"))) {
                taskStatus = "error";
            } else {
                taskStatus = "running";
            }
            // status 过滤
            if (status != null && !status.isBlank() && !status.equals(taskStatus)) {
                continue;
            }

            // 成功率（24h）
            long total24h = toLong(row.getOrDefault("total_24h", 0L));
            long success24h = toLong(row.getOrDefault("success_24h", 0L));
            int successRate = total24h > 0 ? (int) (success24h * 100 / total24h) : 100;

            Map<String, Object> task = new HashMap<>();
            task.put("instanceId", instanceId);
            task.put("instanceName", ins.getName());
            task.put("dbType", ins.getDbType());
            task.put("dbVersion", ins.getDbVersion());
            task.put("host", ins.getHost());
            task.put("port", ins.getPort());
            task.put("frequency", freq);
            task.put("frequencyLabel", freqLabel(freq));
            task.put("status", taskStatus);
            task.put("lastCollectTime", row.get("last_collect_time"));
            task.put("lastDurationMs", row.get("last_duration_ms"));
            task.put("lastMetricCount", row.get("last_metric_count"));
            task.put("lastSuccess", row.get("last_success"));
            task.put("lastErrorMessage", row.get("last_error_message"));
            task.put("successRate", successRate);
            task.put("total24h", total24h);
            task.put("success24h", success24h);
            result.add(task);
        }
        return Result.ok(result);
    }

    /**
     * 单实例单频率的历史日志（倒序，默认最近 50 条）。
     * 日志弹窗数据来源。
     */
    @Operation(summary = "采集历史日志", description = "返回单实例单频率的近期采集运行记录，供日志弹窗展示")
    @PostMapping("/query")
    public Result<List<CollectLog>> logs(@Valid @RequestBody CollectLogQueryRequest req) {
        int limit = req.getLimit() == null || req.getLimit() <= 0 ? 50 : Math.min(req.getLimit(), 200);
        return Result.ok(collectLogMapper.selectRecent(req.getInstanceId(), req.getFrequency(), limit));
    }

    // ── 工具方法 ─────────────────────────────────────────────────────────────

    private static String freqLabel(String freq) {
        return switch (freq) {
            case "1m" -> "分钟级（1m）";
            case "1h" -> "小时级（1h）";
            case "1d" -> "天级（1d）";
            default   -> freq;
        };
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0L; }
    }
}
