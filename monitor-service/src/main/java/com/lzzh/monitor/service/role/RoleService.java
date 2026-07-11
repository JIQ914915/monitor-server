package com.lzzh.monitor.service.role;

import com.lzzh.monitor.api.request.RolePageRequest;
import com.lzzh.monitor.api.request.RoleRequest;
import com.lzzh.monitor.api.response.RoleVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 角色管理服务（权限码 menu:action 集合，§11.11.6）。 */
public interface RoleService {

    /**
     * 分页查询角色。
     *
     * @param param 分页与过滤条件（关键字/类型/状态）
     * @return 角色分页结果
     */
    PageResult<RoleVo> page(RolePageRequest param);

    /**
     * 全部角色（用于用户表单的角色下拉）。
     *
     * @return 全部角色列表
     */
    List<RoleVo> listAll();

    /**
     * 新增角色。
     *
     * @param role 角色信息及权限配置
     * @return 新建角色的主键 ID
     */
    Long create(RoleRequest role);

    /**
     * 修改角色。
     *
     * @param role 角色信息及权限配置（须含主键 ID）
     */
    void update(RoleRequest role);

    /**
     * 删除角色。
     *
     * @param id 角色主键 ID
     */
    void delete(Long id);

    /**
     * 启用/停用角色。
     *
     * @param id     角色主键 ID
     * @param status 目标状态：enabled 启用 / disabled 停用
     */
    void toggleStatus(Long id, String status);
}
