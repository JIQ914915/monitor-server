package com.lzzh.monitor.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.LoginRequest;
import com.lzzh.monitor.api.response.MenuVo;
import com.lzzh.monitor.api.response.SysMenuVo;
import com.lzzh.monitor.api.response.UserInfoVo;
import com.lzzh.monitor.admin.security.LoginUser;
import com.lzzh.monitor.admin.security.RolePermissionResolver;
import com.lzzh.monitor.admin.security.SecurityUtils;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.common.result.ResultCode;
import com.lzzh.monitor.common.security.JwtUtil;
import com.lzzh.monitor.dao.entity.SysUser;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.service.menu.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 认证与当前用户上下文。 */
@Tag(name = "认证管理", description = "登录认证、当前用户信息与菜单获取")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private RolePermissionResolver permissionResolver;
    @Resource
    private MenuService menuService;

    /**
     * 用户登录。
     *
     * @param req 登录入参（用户名、密码）
     * @return 登录成功返回包含 JWT token 的键值对（key 为 token）
     */
    @Operation(summary = "用户登录", description = "校验用户名密码，成功返回 JWT token")
    @PostMapping("/login")
    public Result<Map<String, String>> login(@Valid @RequestBody LoginRequest req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.code(), "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("账号已禁用");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(java.time.LocalDateTime.now());
        userMapper.updateById(update);
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return Result.ok(Map.of("token", token));
    }

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前用户信息（ID、用户名、昵称、角色、权限）
     */
    @Operation(summary = "获取当前用户信息", description = "返回当前登录用户的基本信息、角色与权限")
    @GetMapping("/info")
    public Result<UserInfoVo> info() {
        LoginUser u = SecurityUtils.current();
        return Result.ok(new UserInfoVo(u.id(), u.username(), u.nickname(), u.roles(), u.permissions()));
    }

    /**
     * 获取当前用户可见菜单树。
     *
     * @return 按 parent_id 递归装配的前端菜单/路由树
     */
    @Operation(summary = "获取菜单树", description = "返回启用状态的菜单，按父子关系递归装配为前端路由树")
    @GetMapping("/menus")
    public Result<List<MenuVo>> menus() {
        // 层级/路由/组件/显隐/高亮 全部来自 sys_menu 表，后端只做「按 parent_id 递归装配」，不含任何硬编码结构。
        List<SysMenuVo> enabled = menuService.listAll().stream()
                .filter(m -> !"disabled".equalsIgnoreCase(m.getStatus()))
                .toList();

        Map<Long, List<SysMenuVo>> byParent = new HashMap<>();
        for (SysMenuVo m : enabled) {
            byParent.computeIfAbsent(m.getParentId(), k -> new ArrayList<>()).add(m);
        }
        return Result.ok(buildChildren(byParent, null));
    }

    /** 按 sort、id 排序后，将某父节点下的菜单行递归装配为前端菜单/路由节点。 */
    private List<MenuVo> buildChildren(Map<Long, List<SysMenuVo>> byParent, Long parentId) {
        List<SysMenuVo> rows = byParent.get(parentId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        rows.sort(Comparator.comparing((SysMenuVo m) -> m.getSort() == null ? 0 : m.getSort())
                .thenComparing(SysMenuVo::getId));
        List<MenuVo> nodes = new ArrayList<>(rows.size());
        for (SysMenuVo m : rows) {
            List<MenuVo> children = buildChildren(byParent, m.getId());
            boolean isGroup = "group".equalsIgnoreCase(m.getMenuType());
            Boolean hidden = Boolean.FALSE.equals(m.getVisible()) ? Boolean.TRUE : null;
            MenuVo.Meta meta = new MenuVo.Meta(m.getName(), m.getIcon(), m.getPerm(), hidden, m.getActiveMenu());
            nodes.add(new MenuVo(m.getRoute(), m.getCode(),
                    isGroup ? null : m.getComponent(), m.getRedirect(),
                    meta, children.isEmpty() ? null : children));
        }
        return nodes;
    }
}
