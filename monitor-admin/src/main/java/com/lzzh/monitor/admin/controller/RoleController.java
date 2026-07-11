package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.RolePageRequest;
import com.lzzh.monitor.api.request.RoleRequest;
import com.lzzh.monitor.api.request.StatusRequest;
import com.lzzh.monitor.api.response.RoleVo;
import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.admin.security.TokenBlacklistService;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.service.role.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色管理（仅管理员可写，§11.11.6）。 */
@Tag(name = "角色管理", description = "角色的增删改查、启停与权限配置")
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;
    private final TokenBlacklistService blacklistService;
    private final SysUserMapper userMapper;

    public RoleController(RoleService roleService, TokenBlacklistService blacklistService,
                          SysUserMapper userMapper) {
        this.roleService = roleService;
        this.blacklistService = blacklistService;
        this.userMapper = userMapper;
    }

    /**
     * 分页查询角色。
     *
     * @param req 分页与过滤条件（关键字/类型/状态），可为空
     * @return 角色分页结果
     */
    @Operation(summary = "分页查询角色", description = "按关键字、角色类型、状态分页检索角色")
    @PostMapping("/page")
    public Result<PageResult<RoleVo>> page(@Valid @RequestBody(required = false) RolePageRequest req) {
        return Result.ok(roleService.page(req == null ? new RolePageRequest() : req));
    }

    /**
     * 查询全部角色（用户表单角色下拉用）。
     *
     * @return 全部角色列表
     */
    @Operation(summary = "查询全部角色", description = "供用户表单的角色下拉使用，返回全部角色")
    @GetMapping("/list")
    public Result<List<RoleVo>> list() {
        return Result.ok(roleService.listAll());
    }

    /**
     * 新增角色。
     *
     * @param req 角色信息及权限配置
     * @return 新建角色的主键 ID
     */
    @Operation(summary = "新增角色", description = "创建一个新的角色并配置其权限")
    @PostMapping
    @RequiresPerm("system_role")
    @OperateLog(module = "角色管理", action = "新增")
    public Result<Long> create(@RequestBody RoleRequest req) {
        return Result.ok(roleService.create(req));
    }

    /**
     * 修改角色。
     *
     * @param req 角色信息及权限配置（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改角色", description = "按主键更新角色信息及权限配置")
    @PostMapping("/update")
    @RequiresPerm("system_role")
    @OperateLog(module = "角色管理", action = "修改")
    public Result<Void> update(@RequestBody RoleRequest req) {
        // 先查出持有该角色的用户，更新后吊销其 Token，迫使重新登录获取最新权限（P0-4）
        List<Long> affectedUserIds = req.getCode() != null
                ? userMapper.listIdsByRoleCode(req.getCode()) : List.of();
        roleService.update(req);
        blacklistService.revokeUsers(affectedUserIds);
        return Result.ok();
    }

    /**
     * 删除角色。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除角色", description = "按主键删除角色，预设角色不可删除")
    @PostMapping("/delete")
    @RequiresPerm("system_role")
    @OperateLog(module = "角色管理", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        roleService.delete(req.getId());
        return Result.ok();
    }

    /**
     * 启用/停用角色。
     *
     * @param req 状态入参（status：enabled 启用 / disabled 停用）
     * @return 空响应体
     */
    @Operation(summary = "启用/停用角色", description = "status=enabled 启用、disabled 停用")
    @PostMapping("/toggle")
    @RequiresPerm("system_role")
    @OperateLog(module = "角色管理", action = "启停")
    public Result<Void> toggle(@Valid @RequestBody StatusRequest req) {
        // 禁用角色时，查出持有该角色的用户并吊销其 Token（P0-4）
        RoleVo role = roleService.listAll().stream()
                .filter(r -> r.getId().equals(req.getId()))
                .findFirst().orElse(null);
        roleService.toggleStatus(req.getId(), req.getStatus());
        if ("disabled".equals(req.getStatus()) && role != null && role.getCode() != null) {
            List<Long> affectedUserIds = userMapper.listIdsByRoleCode(role.getCode());
            blacklistService.revokeUsers(affectedUserIds);
        }
        return Result.ok();
    }
}
