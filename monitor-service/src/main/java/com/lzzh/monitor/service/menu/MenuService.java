package com.lzzh.monitor.service.menu;

import com.lzzh.monitor.api.request.MenuRequest;
import com.lzzh.monitor.api.response.SysMenuVo;
import com.lzzh.monitor.common.result.PageParam;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 菜单管理服务。 */
public interface MenuService {

    /**
     * 分页查询菜单。
     *
     * @param param 分页与关键字过滤条件
     * @return 菜单分页结果
     */
    PageResult<SysMenuVo> page(PageParam param);

    /**
     * 全部启用菜单（用于角色权限配置矩阵 / 动态菜单树）。
     *
     * @return 全部菜单列表
     */
    List<SysMenuVo> listAll();

    /**
     * 新增菜单。
     *
     * @param menu 菜单信息（含按钮权限点）
     * @return 新建菜单的主键 ID
     */
    Long create(MenuRequest menu);

    /**
     * 修改菜单。
     *
     * @param menu 菜单信息（须含主键 ID）
     */
    void update(MenuRequest menu);

    /**
     * 删除菜单。
     *
     * @param id 菜单主键 ID
     */
    void delete(Long id);

    /**
     * 启用/停用菜单。
     *
     * @param id     菜单主键 ID
     * @param status 目标状态：enabled 启用 / disabled 停用
     */
    void toggleStatus(Long id, String status);
}
