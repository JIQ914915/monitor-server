package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.GroupRequest;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.response.GroupOptionVo;
import com.lzzh.monitor.api.response.GroupVo;
import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageParam;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.group.InstanceGroupService;
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

/** 实例分组管理。 */
@Tag(name = "分组管理", description = "实例分组的增删改查")
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    @Resource
    private InstanceGroupService groupService;

    /**
     * 分页查询分组。
     *
     * @param req 分页与关键字条件，可为空
     * @return 分组分页结果
     */
    @Operation(summary = "分页查询分组", description = "按关键字分页检索实例分组")
    @PostMapping("/page")
    public Result<PageResult<GroupVo>> page(@Valid @RequestBody(required = false) PageParam req) {
        return Result.ok(groupService.page(req == null ? new PageParam() : req));
    }

    /**
     * 查询全部分组（父分组下拉、实例表单分组多选用）。
     *
     * @return 全部分组列表
     */
    @Operation(summary = "查询全部分组", description = "供父分组下拉、实例表单分组多选使用，返回全部分组")
    @GetMapping("/list")
    public Result<List<GroupVo>> list() {
        return Result.ok(groupService.listAll());
    }

    /**
     * 分组选项（仅 id + name）。
     *
     * @return 分组选项列表，供角色数据范围、下拉选择使用
     */
    @Operation(summary = "查询分组选项", description = "仅返回 id + name 的轻量分组选项，供角色数据范围等下拉使用")
    @GetMapping("/options")
    public Result<List<GroupOptionVo>> options() {
        return Result.ok(groupService.listOptions());
    }

    /**
     * 新增分组。
     *
     * @param req 分组信息
     * @return 新建分组的主键 ID
     */
    @Operation(summary = "新增分组", description = "创建一个新的实例分组")
    @PostMapping
    @RequiresPerm("system_group")
    @OperateLog(module = "分组管理", action = "新增")
    public Result<Long> create(@RequestBody GroupRequest req) {
        return Result.ok(groupService.create(req));
    }

    /**
     * 修改分组。
     *
     * @param req 分组信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改分组", description = "按主键更新分组信息")
    @PostMapping("/update")
    @RequiresPerm("system_group")
    @OperateLog(module = "分组管理", action = "修改")
    public Result<Void> update(@RequestBody GroupRequest req) {
        groupService.update(req);
        return Result.ok();
    }

    /**
     * 删除分组。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除分组", description = "按主键删除实例分组")
    @PostMapping("/delete")
    @RequiresPerm("system_group")
    @OperateLog(module = "分组管理", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        groupService.delete(req.getId());
        return Result.ok();
    }
}
