package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.ConnectionTestRequest;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.InstancePageRequest;
import com.lzzh.monitor.api.request.InstanceRequest;
import com.lzzh.monitor.api.request.StatusRequest;
import com.lzzh.monitor.api.response.ConnectionTestVo;
import com.lzzh.monitor.api.response.FleetOverviewVo;
import com.lzzh.monitor.api.response.FleetSummaryVo;
import com.lzzh.monitor.api.response.InstanceCapabilityVo;
import com.lzzh.monitor.api.response.InstanceVo;
import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.instance.InstanceCapabilityService;
import com.lzzh.monitor.service.instance.InstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 实例管理 RESTful（写/查询统一 POST + body，§13.3.3；按钮权限 §11.11.6）。 */
@Tag(name = "实例管理", description = "数据库实例的增删改查、启停与连接测试")
@RestController
@RequestMapping("/api/v1/instances")
public class InstanceController {

    @Resource
    private InstanceService instanceService;
    @Resource
    private InstanceCapabilityService instanceCapabilityService;

    /**
     * 分页查询实例。
     *
     * @param req 分页与过滤条件（关键字/类型/状态/分组），可为空
     * @return 实例分页结果
     */
    @Operation(summary = "分页查询实例", description = "按关键字、数据库类型、状态、分组分页检索实例")
    @PostMapping("/page")
    public Result<PageResult<InstanceVo>> page(@Valid @RequestBody(required = false) InstancePageRequest req) {
        return Result.ok(instanceService.page(req == null ? new InstancePageRequest() : req));
    }

    /**
     * 查询全部实例（实例选择面板用）。
     *
     * @return 全部实例列表（密码脱敏）
     */
    @Operation(summary = "查询全部实例", description = "供实例选择面板使用，返回全部实例（密码脱敏）")
    @GetMapping("/all")
    public Result<List<InstanceVo>> all() {
        return Result.ok(instanceService.listAll());
    }

    /**
     * 按 ID 查询实例详情。
     *
     * @param req 主键入参
     * @return 实例详情
     */
    @Operation(summary = "查询实例详情", description = "按主键 ID 查询单个实例")
    @PostMapping("/get")
    public Result<InstanceVo> get(@Valid @RequestBody IdRequest req) {
        return Result.ok(instanceService.getById(req.getId()));
    }

    /**
     * 新增实例。
     *
     * @param req 实例信息
     * @return 新建实例的主键 ID
     */
    @Operation(summary = "新增实例", description = "创建一个新的数据库实例")
    @PostMapping
    @RequiresPerm("instance:add")
    @OperateLog(module = "实例管理", action = "新增")
    public Result<Long> create(@RequestBody InstanceRequest req) {
        return Result.ok(instanceService.create(req));
    }

    /**
     * 修改实例。
     *
     * @param req 实例信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改实例", description = "按主键更新实例信息")
    @PostMapping("/update")
    @RequiresPerm("instance:edit")
    @OperateLog(module = "实例管理", action = "修改")
    public Result<Void> update(@RequestBody InstanceRequest req) {
        instanceService.update(req);
        return Result.ok();
    }

    /**
     * 删除实例。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除实例", description = "按主键删除实例")
    @PostMapping("/delete")
    @RequiresPerm("instance:delete")
    @OperateLog(module = "实例管理", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        instanceService.delete(req.getId());
        return Result.ok();
    }

    /**
     * 暂停/恢复采集。
     *
     * @param req 状态入参（status：normal 恢复采集 / paused 暂停采集）
     * @return 空响应体
     */
    @Operation(summary = "暂停/恢复采集", description = "status=paused 暂停采集、normal 恢复采集；暂停后实例不再纳入任何频率的采集周期")
    @PostMapping("/toggle")
    @RequiresPerm("instance:edit")
    @OperateLog(module = "实例管理", action = "启停")
    public Result<Void> toggle(@Valid @RequestBody StatusRequest req) {
        instanceService.toggleStatus(req.getId(), req.getStatus());
        return Result.ok();
    }

    /**
     * 测试实例连接（含采集账号权限逐项检测）。
     *
     * @param req 连接测试入参（类型/主机/端口/账号/密码）
     * @return 连接成功返回数据库版本号 + 权限检测项列表
     */
    @Operation(summary = "测试实例连接", description = "校验连接参数，成功返回数据库版本号与采集账号权限逐项检测结果")
    @PostMapping("/test-connection")
    public Result<ConnectionTestVo> testConnection(@Valid @RequestBody ConnectionTestRequest req) {
        return Result.ok(instanceService.testConnection(req));
    }

    /**
     * 检测实例运行时能力状态（需求 §20：能力矩阵 + 页面降级）。
     *
     * @param req 主键入参
     * @return 各监控能力状态列表（status 见字典 capability_status）
     */
    @Operation(summary = "实例能力状态检测",
            description = "组合版本、主机关联与最近采集日志，返回 Top SQL/慢SQL样本/锁分析/错误日志/主机监控等能力状态，供页面降级提示")
    @PostMapping("/capabilities")
    public Result<List<InstanceCapabilityVo>> capabilities(@Valid @RequestBody IdRequest req) {
        return Result.ok(instanceCapabilityService.detect(req.getId()));
    }

    /**
     * 查询实例舰队概况（仪表盘用）。
     *
     * @return 各状态计数 + 平均健康度 + 健康等级分布
     */
    @Operation(summary = "实例概况", description = "返回各状态计数（normal/abnormal/paused）、平均健康度及健康等级分布，供仪表盘展示")
    @GetMapping("/summary")
    public Result<FleetSummaryVo> summary() {
        return Result.ok(instanceService.summary());
    }

    /**
     * 首页全局总览（§11.1.2）。
     *
     * @return 整体健康门面 + 状态统计卡 + 数据库类型分布 + 高风险实例 Top10
     */
    @Operation(summary = "首页全局总览",
            description = "整体健康门面（聚合健康分+五维达标率）、5 张状态统计卡、数据库类型分布、高风险实例 Top10；数据范围为当前用户数据权限内实例")
    @PostMapping("/fleet-overview")
    public Result<FleetOverviewVo> fleetOverview() {
        return Result.ok(instanceService.fleetOverview());
    }
}
