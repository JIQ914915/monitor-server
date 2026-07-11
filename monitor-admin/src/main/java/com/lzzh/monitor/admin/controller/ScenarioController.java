package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.api.request.ScenarioDetailRequest;
import com.lzzh.monitor.api.request.ScenarioPageRequest;
import com.lzzh.monitor.api.request.ScenarioThresholdRequest;
import com.lzzh.monitor.api.request.ScenarioToggleRequest;
import com.lzzh.monitor.api.response.ScenarioPageVo;
import com.lzzh.monitor.api.response.ScenarioVo;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.scenario.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场景管理 API（§11.8 场景化监控）。
 * <p>实例级页面：多信号复合诊断场景的实时状态查看与启停。
 */
@Tag(name = "场景管理", description = "多信号复合诊断场景的查询与启停")
@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @Operation(summary = "查询实例适配的场景列表",
            description = "返回场景列表（含各信号实时状态、当前触发状态、触发次数）与统计卡片数据，场景数量少不分页")
    @PostMapping("/page")
    @RequiresPerm("scenario_mgmt:view")
    public Result<ScenarioPageVo> page(@Valid @RequestBody ScenarioPageRequest req) {
        return Result.ok(scenarioService.page(req));
    }

    @Operation(summary = "查询场景详情", description = "列表字段 + 诊断结论 + 关联知识库文章列表")
    @PostMapping("/detail")
    @RequiresPerm("scenario_mgmt:view")
    public Result<ScenarioVo> detail(@Valid @RequestBody ScenarioDetailRequest req) {
        return Result.ok(scenarioService.detail(req));
    }

    @Operation(summary = "启停场景", description = "写实例级配置；停用时联动关闭该场景在此实例的活跃综合事件")
    @OperateLog(module = "场景管理", action = "启停")
    @PostMapping("/toggle")
    @RequiresPerm("scenario_mgmt:toggle")
    public Result<Boolean> toggle(@Valid @RequestBody ScenarioToggleRequest req) {
        return Result.ok(scenarioService.toggle(req));
    }

    @Operation(summary = "一键开启常用场景",
            description = "开启系统推荐（recommended）且适配当前实例的内置场景（未关联主机的实例跳过主机类场景）；已启用的跳过，返回本次新开启数量")
    @OperateLog(module = "场景管理", action = "一键开启常用")
    @PostMapping("/enable-recommended")
    @RequiresPerm("scenario_mgmt:toggle")
    public Result<Integer> enableRecommended(@Valid @RequestBody ScenarioPageRequest req) {
        return Result.ok(scenarioService.enableRecommended(req.getInstanceId()));
    }

    @Operation(summary = "调整场景阈值",
            description = "实例级覆盖各信号触发阈值（仅阈值数值，运算符与组合逻辑不可改）；空 overrides 恢复模板默认，下一评估周期生效")
    @OperateLog(module = "场景管理", action = "调整阈值")
    @PostMapping("/thresholds")
    @RequiresPerm("scenario_mgmt:edit")
    public Result<Boolean> updateThresholds(@Valid @RequestBody ScenarioThresholdRequest req) {
        return Result.ok(scenarioService.updateThresholds(req));
    }
}
