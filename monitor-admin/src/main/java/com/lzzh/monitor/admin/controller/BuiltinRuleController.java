package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.BuiltinRulePageRequest;
import com.lzzh.monitor.api.request.BuiltinRuleSaveRequest;
import com.lzzh.monitor.api.request.RuleCodeRequest;
import com.lzzh.monitor.api.response.AlertRuleVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.alert.AlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内置规则管理 API（系统设置 → 内置规则管理，全局视角）。
 * <p>直接维护 alert_rule 模板表：适用类型/版本、默认阈值、目标库 SQL 评估配置。
 * 实例级启停与阈值覆盖仍走 {@code /alerts/rules} 接口，互不影响。
 */
@Tag(name = "内置规则管理", description = "内置告警规则模板全局维护（产品库指标 / 目标库 SQL 两种数据来源）")
@RestController
@RequestMapping("/api/v1/alerts/builtin-rules")
public class BuiltinRuleController {

    private final AlertRuleService alertRuleService;

    public BuiltinRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @Operation(summary = "分页查询内置规则模板", description = "全局视角，支持按类型/级别/数据来源过滤")
    @PostMapping("/page")
    @RequiresPerm("builtin_rule:list")
    public Result<PageResult<AlertRuleVo>> page(@RequestBody(required = false) BuiltinRulePageRequest req) {
        return Result.ok(alertRuleService.pageBuiltinTemplates(req == null ? new BuiltinRulePageRequest() : req));
    }

    @Operation(summary = "新建内置规则模板")
    @PostMapping("/create")
    @RequiresPerm("builtin_rule:create")
    @OperateLog(module = "内置规则管理", action = "新增")
    public Result<AlertRuleVo> create(@Valid @RequestBody BuiltinRuleSaveRequest req) {
        req.setId(null);
        return Result.ok(alertRuleService.saveBuiltinTemplate(req));
    }

    @Operation(summary = "更新内置规则模板", description = "规则编码不可修改；须携带模板 id")
    @PostMapping("/update")
    @RequiresPerm("builtin_rule:update")
    @OperateLog(module = "内置规则管理", action = "修改")
    public Result<AlertRuleVo> update(@Valid @RequestBody BuiltinRuleSaveRequest req) {
        if (req.getId() == null) {
            throw new IllegalArgumentException("更新内置规则必须携带 id");
        }
        return Result.ok(alertRuleService.saveBuiltinTemplate(req));
    }

    @Operation(summary = "删除内置规则模板", description = "级联关闭各实例活跃事件并清理实例配置")
    @PostMapping("/delete")
    @RequiresPerm("builtin_rule:delete")
    @OperateLog(module = "内置规则管理", action = "删除")
    public Result<Void> delete(@Valid @RequestBody RuleCodeRequest req) {
        alertRuleService.deleteBuiltinTemplate(req.getRuleCode().trim());
        return Result.ok();
    }
}
