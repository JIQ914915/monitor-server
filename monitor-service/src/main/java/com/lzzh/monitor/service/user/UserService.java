package com.lzzh.monitor.service.user;

import com.lzzh.monitor.api.request.UserPageRequest;
import com.lzzh.monitor.api.request.UserRequest;
import com.lzzh.monitor.api.response.UserOptionVo;
import com.lzzh.monitor.api.response.UserVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 用户管理服务（多角色 roles，§5.5）。 */
public interface UserService {

    /**
     * 分页查询用户。
     *
     * @param param 分页与过滤条件
     * @return 用户分页结果
     */
    PageResult<UserVo> page(UserPageRequest param);

    /**
     * 用户选项列表（仅启用用户，id + 展示名），供负责人/成员等下拉选择，避免借用分页接口。
     *
     * @return 用户选项列表
     */
    List<UserOptionVo> listOptions();

    /**
     * 按主键查询用户。
     *
     * @param id 用户主键 ID
     * @return 用户详情
     */
    UserVo getById(Long id);

    /**
     * 新增用户；password 已由上层加密。
     *
     * @param user 用户信息（口令已加密）
     * @return 新建用户的主键 ID
     */
    Long create(UserRequest user);

    /**
     * 修改用户；password 为空则不更新口令。
     *
     * @param user 用户信息（须含主键 ID）
     */
    void update(UserRequest user);

    /**
     * 删除用户。
     *
     * @param id 用户主键 ID
     */
    void delete(Long id);

    /**
     * 启用/停用用户。
     *
     * @param id      用户主键 ID
     * @param enabled 是否启用：true 启用 / false 停用
     */
    void toggleEnabled(Long id, boolean enabled);

    /**
     * 重置用户口令（口令已加密）。
     *
     * @param id              用户主键 ID
     * @param encodedPassword 已加密的口令
     */
    void resetPassword(Long id, String encodedPassword);
}
