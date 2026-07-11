package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.request.RoleRequest;
import com.lzzh.monitor.api.response.RoleVo;
import com.lzzh.monitor.dao.entity.SysRole;

/** 角色实体 ↔ DTO 转换。 */
public final class RoleConverter {

    private RoleConverter() {
    }

    /**
     * 角色实体转响应 VO。
     *
     * @param e         角色实体，可为 null
     * @param userCount 关联用户数，由 service 层统计后传入（轻量列表场景可传 null）
     * @return 角色响应 VO；入参为 null 时返回 null
     */
    public static RoleVo toVo(SysRole e, Long userCount) {
        if (e == null) {
            return null;
        }
        RoleVo v = new RoleVo();
        v.setId(e.getId());
        v.setCode(e.getCode());
        v.setName(e.getName());
        v.setType(e.getType());
        v.setStatus(e.getStatus());
        v.setDescription(e.getDescription());
        v.setPermissions(e.getPermissions());
        v.setDataScope(e.getDataScope());
        v.setDataScopeGroups(e.getDataScopeGroups());
        v.setUserCount(userCount);
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /**
     * 角色请求 DTO 转实体（不含时间字段）。
     *
     * @param r 角色请求 DTO，可为 null
     * @return 角色实体；入参为 null 时返回 null
     */
    public static SysRole toEntity(RoleRequest r) {
        if (r == null) {
            return null;
        }
        SysRole e = new SysRole();
        e.setId(r.getId());
        e.setCode(r.getCode());
        e.setName(r.getName());
        e.setType(r.getType());
        e.setStatus(r.getStatus());
        e.setDescription(r.getDescription());
        e.setPermissions(r.getPermissions());
        e.setDataScope(r.getDataScope());
        e.setDataScopeGroups(r.getDataScopeGroups());
        return e;
    }
}
