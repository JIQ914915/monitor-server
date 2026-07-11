package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.RetentionRequest;
import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.retention.RetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据保留策略（系统级，仅管理员可写，§12.2）。 */
@Tag(name = "数据保留", description = "系统级数据保留策略的查询与保存")
@RestController
@RequestMapping("/api/v1/retention")
public class RetentionController {

    @Resource
    private RetentionService retentionService;

    /**
     * 查询数据保留策略。
     *
     * @return 结构为 { factory: {category:days}, configs: [...] }，factory 为出厂默认，configs 为当前配置
     */
    @Operation(summary = "查询数据保留策略", description = "返回出厂默认（factory）与当前配置（configs）")
    @GetMapping("/list")
    public Result<Map<String, Object>> list() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("factory", retentionService.factory());
        body.put("configs", retentionService.list());
        return Result.ok(body);
    }

    /**
     * 保存数据保留策略。
     *
     * @param configs 各类别保留策略配置列表（按 category upsert）
     * @return 空响应体
     */
    @Operation(summary = "保存数据保留策略", description = "批量保存各类别的保留策略配置，按 category 做 upsert")
    @PostMapping("/save")
    @RequiresPerm("data_retention")
    @OperateLog(module = "数据保留", action = "保存配置")
    public Result<Void> save(@RequestBody List<RetentionRequest> configs) {
        retentionService.save(configs);
        return Result.ok();
    }
}
