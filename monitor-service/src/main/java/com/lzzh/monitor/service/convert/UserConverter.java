package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.request.UserRequest;
import com.lzzh.monitor.api.response.UserVo;
import com.lzzh.monitor.dao.entity.SysUser;

/** 用户实体 ↔ DTO 转换。 */
public final class UserConverter {

    private UserConverter() {
    }

    /**
     * 实体转响应 VO（不含密码）。
     *
     * @param e 用户实体，可为 null
     * @return 用户响应 VO；入参为 null 时返回 null
     */
    public static UserVo toVo(SysUser e) {
        if (e == null) {
            return null;
        }
        UserVo v = new UserVo();
        v.setId(e.getId());
        v.setUsername(e.getUsername());
        v.setNickname(e.getNickname());
        v.setEmail(e.getEmail());
        v.setPhone(e.getPhone());
        v.setRoles(e.getRoles());
        v.setEnabled(e.getEnabled());
        v.setLastLoginTime(e.getLastLoginTime());
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /**
     * 请求 DTO 转实体；password 原样带出（由控制器完成加密后回填到 request）。
     *
     * @param r 用户请求 DTO，可为 null
     * @return 用户实体；入参为 null 时返回 null
     */
    public static SysUser toEntity(UserRequest r) {
        if (r == null) {
            return null;
        }
        SysUser e = new SysUser();
        e.setId(r.getId());
        e.setUsername(r.getUsername());
        e.setNickname(r.getNickname());
        e.setEmail(r.getEmail());
        e.setPhone(r.getPhone());
        e.setPassword(r.getPassword());
        e.setRoles(r.getRoles());
        e.setEnabled(r.getEnabled());
        return e;
    }
}
