package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.HostConnectionTestRequest;
import com.lzzh.monitor.api.request.HostPageRequest;
import com.lzzh.monitor.api.request.HostRequest;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.StatusRequest;
import com.lzzh.monitor.api.response.HostOptionVo;
import com.lzzh.monitor.api.response.HostVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.host.HostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 主机管理（主机登记、启停、exporter 连通性测试）。 */
@Tag(name = "主机管理", description = "数据库主机登记与主机指标采集配置")
@RestController
@RequestMapping("/api/v1/hosts")
public class HostController {

    private final HostService hostService;

    public HostController(HostService hostService) {
        this.hostService = hostService;
    }

    /**
     * 分页查询主机。
     *
     * @param req 分页与过滤条件（关键字/状态/采集方式），可为空
     * @return 主机分页结果（含关联实例数）
     */
    @Operation(summary = "分页查询主机", description = "按关键字、状态、采集方式分页检索主机")
    @PostMapping("/page")
    public Result<PageResult<HostVo>> page(@Valid @RequestBody(required = false) HostPageRequest req) {
        return Result.ok(hostService.page(req == null ? new HostPageRequest() : req));
    }

    /**
     * 按 ID 查询主机详情。
     *
     * @param req 主键入参
     * @return 主机详情
     */
    @Operation(summary = "查询主机详情", description = "按主键 ID 查询单个主机")
    @PostMapping("/get")
    public Result<HostVo> get(@Valid @RequestBody IdRequest req) {
        return Result.ok(hostService.getById(req.getId()));
    }

    /**
     * 主机下拉选项（实例表单「所在主机」选择用）。
     *
     * @return 全部主机的轻量选项列表
     */
    @Operation(summary = "查询主机选项", description = "仅返回 id + name + ip 的轻量选项，供实例表单选择所在主机")
    @PostMapping("/options")
    public Result<List<HostOptionVo>> options() {
        return Result.ok(hostService.listOptions());
    }

    /**
     * 新增主机。
     *
     * @param req 主机信息
     * @return 新建主机的主键 ID
     */
    @Operation(summary = "新增主机", description = "登记一台数据库主机")
    @PostMapping
    @RequiresPerm("host:add")
    @OperateLog(module = "主机管理", action = "新增")
    public Result<Long> create(@RequestBody HostRequest req) {
        return Result.ok(hostService.create(req));
    }

    /**
     * 修改主机。
     *
     * @param req 主机信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改主机", description = "按主键更新主机信息")
    @PostMapping("/update")
    @RequiresPerm("host:edit")
    @OperateLog(module = "主机管理", action = "修改")
    public Result<Void> update(@RequestBody HostRequest req) {
        hostService.update(req);
        return Result.ok();
    }

    /**
     * 删除主机（仍有实例关联时拒绝并提示）。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除主机", description = "按主键删除主机；仍有实例关联时拒绝删除")
    @PostMapping("/delete")
    @RequiresPerm("host:delete")
    @OperateLog(module = "主机管理", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        hostService.delete(req.getId());
        return Result.ok();
    }

    /**
     * 暂停/恢复主机指标采集。
     *
     * @param req 状态入参（status：normal 恢复采集 / paused 暂停采集）
     * @return 空响应体
     */
    @Operation(summary = "暂停/恢复采集", description = "status=paused 暂停主机指标采集、normal 恢复采集")
    @PostMapping("/toggle")
    @RequiresPerm("host:edit")
    @OperateLog(module = "主机管理", action = "启停")
    public Result<Void> toggle(@Valid @RequestBody StatusRequest req) {
        hostService.toggleStatus(req.getId(), req.getStatus());
        return Result.ok();
    }

    /**
     * 测试 exporter 连通性。
     *
     * @param req 连通性测试入参（IP/端口/路径）
     * @return 成功返回 exporter 版本与可解析指标行数
     */
    @Operation(summary = "测试 exporter 连通性", description = "HTTP 探测主机 node_exporter /metrics，成功返回版本与可解析指标行数")
    @PostMapping("/test-connection")
    @RequiresPerm("host:test")
    public Result<String> testConnection(@Valid @RequestBody HostConnectionTestRequest req) {
        return Result.ok(hostService.testConnection(req));
    }
}
