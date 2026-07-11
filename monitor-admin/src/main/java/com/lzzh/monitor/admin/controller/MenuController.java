package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.MenuRequest;
import com.lzzh.monitor.api.request.StatusRequest;
import com.lzzh.monitor.api.response.SysMenuVo;
import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageParam;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.menu.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 菜单管理（仅管理员可写）。 */
@Tag(name = "菜单管理", description = "系统菜单的分页查询、列表、增删改与启停（仅管理员可写）")
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 分页查询菜单。
     *
     * @param req 分页与关键字过滤条件，可为空
     * @return 菜单分页结果
     */
    @Operation(summary = "分页查询菜单", description = "按关键字（名称/编码）分页检索系统菜单")
    @PostMapping("/page")
    public Result<PageResult<SysMenuVo>> page(@Valid @RequestBody(required = false) PageParam req) {
        return Result.ok(menuService.page(req == null ? new PageParam() : req));
    }

    /**
     * 查询全部菜单。
     *
     * @return 全部菜单列表（用于角色权限配置矩阵 / 动态菜单树）
     */
    @Operation(summary = "查询全部菜单", description = "返回全部菜单，供角色权限配置矩阵与动态菜单树使用")
    @GetMapping("/list")
    public Result<List<SysMenuVo>> list() {
        return Result.ok(menuService.listAll());
    }

    /**
     * 新增菜单。
     *
     * @param req 菜单信息（含按钮权限点）
     * @return 新建菜单的主键 ID
     */
    @Operation(summary = "新增菜单", description = "创建一个新的系统菜单，菜单编码需唯一")
    @PostMapping
    @RequiresPerm("system_menu")
    @OperateLog(module = "菜单管理", action = "新增")
    public Result<Long> create(@RequestBody MenuRequest req) {
        return Result.ok(menuService.create(req));
    }

    /**
     * 修改菜单。
     *
     * @param req 菜单信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改菜单", description = "按主键更新菜单信息")
    @PostMapping("/update")
    @RequiresPerm("system_menu")
    @OperateLog(module = "菜单管理", action = "修改")
    public Result<Void> update(@RequestBody MenuRequest req) {
        menuService.update(req);
        return Result.ok();
    }

    /**
     * 删除菜单。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除菜单", description = "按主键删除菜单")
    @PostMapping("/delete")
    @RequiresPerm("system_menu")
    @OperateLog(module = "菜单管理", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        menuService.delete(req.getId());
        return Result.ok();
    }

    /**
     * 启用/停用菜单。
     *
     * @param req 状态入参（status：enabled 启用 / disabled 停用）
     * @return 空响应体
     */
    @Operation(summary = "启用/停用菜单", description = "status=enabled 启用、disabled 停用")
    @PostMapping("/toggle")
    @RequiresPerm("system_menu")
    @OperateLog(module = "菜单管理", action = "启停")
    public Result<Void> toggle(@Valid @RequestBody StatusRequest req) {
        menuService.toggleStatus(req.getId(), req.getStatus());
        return Result.ok();
    }
}
