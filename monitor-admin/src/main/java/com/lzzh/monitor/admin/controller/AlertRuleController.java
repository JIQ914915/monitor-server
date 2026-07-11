package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.AlertRulePageRequest;
import com.lzzh.monitor.api.request.AlertRuleSaveRequest;
import com.lzzh.monitor.api.response.AlertRuleVo;
import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.alert.AlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 告警规则管理 API。 */
@Tag(name = "告警规则管理", description = "告警规则 CRUD 与启停")
@RestController
@RequestMapping("/api/v1/alerts/rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @Operation(summary = "分页查询告警规则")
    @PostMapping("/page")
    @RequiresPerm("alert_rule:view")
    public Result<PageResult<AlertRuleVo>> page(@RequestBody(required = false) AlertRulePageRequest req) {
        return Result.ok(alertRuleService.page(req == null ? new AlertRulePageRequest() : req));
    }

    @Operation(summary = "查询规则详情")
    @GetMapping("/{ruleCode}")
    @RequiresPerm("alert_rule:view")
    public Result<AlertRuleVo> detail(@PathVariable String ruleCode) {
        return Result.ok(alertRuleService.getByRuleCode(ruleCode));
    }

    @Operation(summary = "新建或更新告警规则")
    @PostMapping("/save")
    @RequiresPerm("alert_rule:edit")
    @OperateLog(module = "告警规则管理", action = "保存")
    public Result<AlertRuleVo> save(@Valid @RequestBody AlertRuleSaveRequest req) {
        return Result.ok(alertRuleService.save(req));
    }

    @Operation(summary = "切换规则启用/停用",
            description = "内置规则传 instanceId 时写 per-instance 覆盖；自定义规则直接修改全局状态")
    @PutMapping("/{ruleCode}/toggle")
    @RequiresPerm("alert_rule:edit")
    @OperateLog(module = "告警规则管理", action = "启停")
    public Result<Void> toggle(@PathVariable String ruleCode, @RequestBody Map<String, Object> body) {
        Object enabledObj = body.get("enabled");
        if (!(enabledObj instanceof Boolean enabled)) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        Object instObj = body.get("instanceId");
        Long instanceId = instObj instanceof Number n ? n.longValue() : null;
        alertRuleService.toggleEnabled(ruleCode, enabled, instanceId);
        return Result.ok();
    }

    @Operation(summary = "一键开启常用规则",
            description = "开启系统推荐（recommended）且适配当前实例的内置规则；已启用的跳过，返回本次新开启数量")
    @PostMapping("/enable-recommended")
    @RequiresPerm("alert_rule:edit")
    @OperateLog(module = "告警规则管理", action = "一键开启常用")
    public Result<Integer> enableRecommended(@RequestBody Map<String, Object> body) {
        Object instObj = body.get("instanceId");
        if (!(instObj instanceof Number n)) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        return Result.ok(alertRuleService.enableRecommended(n.longValue()));
    }

    @Operation(summary = "删除告警规则（内置规则不可删除）")
    @DeleteMapping("/{ruleCode}")
    @RequiresPerm("alert_rule:edit")
    @OperateLog(module = "告警规则管理", action = "删除")
    public Result<Void> delete(@PathVariable String ruleCode) {
        alertRuleService.delete(ruleCode);
        return Result.ok();
    }

    @Operation(summary = "更新规则扫描间隔（分钟）")
    @PutMapping("/{ruleCode}/scan-interval")
    @RequiresPerm("alert_rule:edit")
    @OperateLog(module = "告警规则管理", action = "调整扫描间隔")
    public Result<Void> updateScanInterval(@PathVariable String ruleCode, @RequestBody Map<String, Object> body) {
        Object intervalObj = body.get("scanIntervalMin");
        if (!(intervalObj instanceof Number n)) {
            throw new IllegalArgumentException("scanIntervalMin 不能为空");
        }
        Object instObj = body.get("instanceId");
        Long instanceId = instObj instanceof Number m ? m.longValue() : null;
        alertRuleService.updateScanInterval(ruleCode, n.intValue(), instanceId);
        return Result.ok();
    }
}
