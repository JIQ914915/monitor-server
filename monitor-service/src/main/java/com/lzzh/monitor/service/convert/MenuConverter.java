package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.request.MenuButtonDto;
import com.lzzh.monitor.api.request.MenuRequest;
import com.lzzh.monitor.api.response.MenuButtonVo;
import com.lzzh.monitor.api.response.SysMenuVo;
import com.lzzh.monitor.dao.entity.MenuButton;
import com.lzzh.monitor.dao.entity.SysMenu;

/** 菜单实体 ↔ DTO 转换（含按钮权限点）。 */
public final class MenuConverter {

    private MenuConverter() {
    }

    /**
     * 菜单实体转响应 VO（含按钮权限点）。
     *
     * @param e 菜单实体
     * @return 菜单响应 VO；入参为 null 时返回 null
     */
    public static SysMenuVo toVo(SysMenu e) {
        if (e == null) {
            return null;
        }
        SysMenuVo v = new SysMenuVo();
        v.setId(e.getId());
        v.setName(e.getName());
        v.setCode(e.getCode());
        v.setType(e.getType());
        v.setMenuType(e.getMenuType());
        v.setParentId(e.getParentId());
        v.setIcon(e.getIcon());
        v.setRoute(e.getRoute());
        v.setComponent(e.getComponent());
        v.setRedirect(e.getRedirect());
        v.setPerm(e.getPerm());
        v.setSort(e.getSort());
        v.setVisible(e.getVisible());
        v.setActiveMenu(e.getActiveMenu());
        v.setStatus(e.getStatus());
        v.setDescription(e.getDescription());
        if (e.getButtons() != null) {
            v.setButtons(e.getButtons().stream().map(MenuConverter::toButtonVo).toList());
        }
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /**
     * 菜单请求转实体（含按钮权限点）。
     *
     * @param r 菜单请求
     * @return 菜单实体；入参为 null 时返回 null
     */
    public static SysMenu toEntity(MenuRequest r) {
        if (r == null) {
            return null;
        }
        SysMenu e = new SysMenu();
        e.setId(r.getId());
        e.setName(r.getName());
        e.setCode(r.getCode());
        e.setType(r.getType());
        e.setMenuType(r.getMenuType());
        e.setParentId(r.getParentId());
        e.setIcon(r.getIcon());
        e.setRoute(r.getRoute());
        e.setComponent(r.getComponent());
        e.setRedirect(r.getRedirect());
        e.setPerm(r.getPerm());
        e.setSort(r.getSort());
        e.setVisible(r.getVisible());
        e.setActiveMenu(r.getActiveMenu());
        e.setStatus(r.getStatus());
        e.setDescription(r.getDescription());
        if (r.getButtons() != null) {
            e.setButtons(r.getButtons().stream().map(MenuConverter::toButtonEntity).toList());
        }
        return e;
    }

    private static MenuButtonVo toButtonVo(MenuButton e) {
        MenuButtonVo v = new MenuButtonVo();
        v.setName(e.getName());
        v.setCode(e.getCode());
        v.setStatus(e.getStatus());
        return v;
    }

    private static MenuButton toButtonEntity(MenuButtonDto d) {
        MenuButton e = new MenuButton();
        e.setName(d.getName());
        e.setCode(d.getCode());
        e.setStatus(d.getStatus());
        return e;
    }
}
