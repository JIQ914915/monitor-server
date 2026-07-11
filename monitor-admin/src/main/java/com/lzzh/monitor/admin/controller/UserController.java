package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.EnabledRequest;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.UserPageRequest;
import com.lzzh.monitor.api.request.UserRequest;
import com.lzzh.monitor.api.response.UserOptionVo;
import com.lzzh.monitor.api.response.UserVo;
import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.admin.security.TokenBlacklistService;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户管理（仅管理员可写，§5.5）。 */
@Tag(name = "用户管理", description = "系统用户的增删改查、启停与密码重置")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    /** 重置/新建用户的默认口令。 */
    private static final String DEFAULT_PASSWORD = "123456";

    @Resource
    private UserService userService;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private TokenBlacklistService blacklistService;

    /**
     * 分页查询用户。
     *
     * @param req 分页与过滤条件（关键字/角色/启停状态），可为空
     * @return 用户分页结果
     */
    @Operation(summary = "分页查询用户", description = "按关键字、角色、启停状态分页检索用户")
    @PostMapping("/page")
    public Result<PageResult<UserVo>> page(@Valid @RequestBody(required = false) UserPageRequest req) {
        return Result.ok(userService.page(req == null ? new UserPageRequest() : req));
    }

    /**
     * 用户选项列表（轻量 id + 展示名），供负责人/成员等下拉选择，避免借用重的分页接口。
     *
     * @return 启用用户的选项列表
     */
    @Operation(summary = "用户选项", description = "返回启用用户的 id + 展示名，供下拉选择")
    @GetMapping("/options")
    public Result<List<UserOptionVo>> options() {
        return Result.ok(userService.listOptions());
    }

    /**
     * 按 ID 查询用户详情。
     *
     * @param req 主键入参
     * @return 用户详情
     */
    @Operation(summary = "查询用户详情", description = "按主键 ID 查询单个用户")
    @PostMapping("/get")
    public Result<UserVo> get(@Valid @RequestBody IdRequest req) {
        return Result.ok(userService.getById(req.getId()));
    }

    /**
     * 新增用户。
     *
     * @param req 用户信息（口令留空则使用默认口令）
     * @return 新建用户的主键 ID
     */
    @Operation(summary = "新增用户", description = "创建一个新用户，口令留空时使用默认口令")
    @PostMapping
    @RequiresPerm("system_user")
    @OperateLog(module = "用户管理", action = "新增")
    public Result<Long> create(@RequestBody UserRequest req) {
        // 口令加密属安全/应用职责，收口在控制器；service 只透传落库
        String raw = StringUtils.hasText(req.getPassword()) ? req.getPassword() : DEFAULT_PASSWORD;
        req.setPassword(passwordEncoder.encode(raw));
        return Result.ok(userService.create(req));
    }

    /**
     * 修改用户。
     *
     * @param req 用户信息（须含主键 ID；口令留空表示不修改）
     * @return 空响应体
     */
    @Operation(summary = "修改用户", description = "按主键更新用户信息，口令留空表示不修改")
    @PostMapping("/update")
    @RequiresPerm("system_user")
    @OperateLog(module = "用户管理", action = "修改")
    public Result<Void> update(@RequestBody UserRequest req) {
        // 留空表示不改口令，置 null 由 service 跳过更新
        req.setPassword(StringUtils.hasText(req.getPassword()) ? passwordEncoder.encode(req.getPassword()) : null);
        userService.update(req);
        return Result.ok();
    }

    /**
     * 删除用户。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除用户", description = "按主键删除用户")
    @PostMapping("/delete")
    @RequiresPerm("system_user")
    @OperateLog(module = "用户管理", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        userService.delete(req.getId());
        return Result.ok();
    }

    /**
     * 启用/停用用户。
     *
     * @param req 启停入参（enabled：true 启用 / false 停用）
     * @return 空响应体
     */
    @Operation(summary = "启用/停用用户", description = "enabled=true 启用、false 停用")
    @PostMapping("/toggle")
    @RequiresPerm("system_user")
    @OperateLog(module = "用户管理", action = "启停")
    public Result<Void> toggle(@Valid @RequestBody EnabledRequest req) {
        userService.toggleEnabled(req.getId(), req.getEnabled());
        if (Boolean.FALSE.equals(req.getEnabled())) {
            // 禁用用户时立即吊销其所有 Token（P0-4）
            blacklistService.revokeUser(req.getId());
        } else {
            // 重新启用时解除封锁，允许用户正常登录
            blacklistService.clearRevocation(req.getId());
        }
        return Result.ok();
    }

    /**
     * 重置用户口令为默认口令。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "重置密码", description = "将指定用户口令重置为系统默认口令")
    @PostMapping("/reset-password")
    @RequiresPerm("system_user")
    @OperateLog(module = "用户管理", action = "重置密码")
    public Result<Void> resetPassword(@Valid @RequestBody IdRequest req) {
        userService.resetPassword(req.getId(), passwordEncoder.encode(DEFAULT_PASSWORD));
        return Result.ok();
    }
}
