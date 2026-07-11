package com.lzzh.monitor.admin.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.common.constant.Constants;
import com.lzzh.monitor.dao.entity.SysRole;
import com.lzzh.monitor.dao.mapper.SysRoleMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 多角色 → 权限并集（§5.5）。super_admin 直接拥有通配权限。 */
@Component
public class RolePermissionResolver {

    private final SysRoleMapper roleMapper;

    public RolePermissionResolver(SysRoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    public List<String> resolve(List<String> roleCodes) {
        if (CollectionUtils.isEmpty(roleCodes)) {
            return Collections.emptyList();
        }
        if (roleCodes.contains(Constants.SUPER_ADMIN_ROLE)) {
            return List.of(Constants.PERM_ALL);
        }
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getCode, roleCodes));
        Set<String> perms = new LinkedHashSet<>();
        for (SysRole r : roles) {
            if (!CollectionUtils.isEmpty(r.getPermissions())) {
                perms.addAll(r.getPermissions());
            }
        }
        return List.copyOf(perms);
    }
}
