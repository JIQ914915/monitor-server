package com.lzzh.monitor.service.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.UserPageRequest;
import com.lzzh.monitor.api.request.UserRequest;
import com.lzzh.monitor.api.response.UserOptionVo;
import com.lzzh.monitor.api.response.UserVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.SysUser;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.service.convert.UserConverter;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private SysUserMapper mapper;

    /**
     * 分页查询用户。
     *
     * @param param 分页与过滤条件（关键字/角色/启停状态）
     * @return 用户分页结果
     */
    @Override
    public PageResult<UserVo> page(UserPageRequest param) {
        Page<SysUser> page = Pages.build(param);
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (param != null && StringUtils.hasText(param.getKeyword())) {
            qw.and(w -> w.like(SysUser::getUsername, param.getKeyword())
                    .or().like(SysUser::getNickname, param.getKeyword())
                    .or().like(SysUser::getEmail, param.getKeyword()));
        }
        if (param != null && param.getEnabled() != null) {
            qw.eq(SysUser::getEnabled, param.getEnabled());
        }
        if (param != null && StringUtils.hasText(param.getRoleCode())) {
            // PG jsonb 数组包含查询：roles @> ["roleCode"]
            qw.apply("roles @> {0}::jsonb", "[\"" + param.getRoleCode().replace("\"", "") + "\"]");
        }
        qw.orderByDesc(SysUser::getId);
        return Pages.toResult(mapper.selectPage(page, qw)).map(UserConverter::toVo);
    }

    /**
     * 用户选项列表（仅启用用户，id + 展示名：昵称优先、无则用户名）。
     *
     * @return 用户选项列表
     */
    @Override
    public List<UserOptionVo> listOptions() {
        return mapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEnabled, true)
                        .orderByAsc(SysUser::getId))
                .stream()
                .map(u -> new UserOptionVo(u.getId(),
                        StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()))
                .toList();
    }

    /**
     * 按主键查询用户，不存在时抛出业务异常。
     *
     * @param id 用户主键 ID
     * @return 用户详情
     */
    @Override
    public UserVo getById(Long id) {
        SysUser u = mapper.selectById(id);
        if (u == null) {
            throw new BusinessException("用户不存在: " + id);
        }
        return UserConverter.toVo(u);
    }

    /**
     * 新增用户；用户名重复时抛出业务异常，enabled 为空默认启用。
     *
     * @param request 用户信息（口令已由上层加密）
     * @return 新建用户的主键 ID
     */
    @Override
    public Long create(UserRequest request) {
        SysUser user = UserConverter.toEntity(request);
        Long count = mapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在: " + user.getUsername());
        }
        if (user.getEnabled() == null) {
            user.setEnabled(true);
        }
        user.setCreateTime(OffsetDateTime.now());
        user.setUpdateTime(OffsetDateTime.now());
        mapper.insert(user);
        return user.getId();
    }

    /**
     * 修改用户；password 为空则不更新口令，避免覆盖为 null。
     *
     * @param request 用户信息（须含主键 ID）
     */
    @Override
    public void update(UserRequest request) {
        SysUser user = UserConverter.toEntity(request);
        // password 为空表示不修改口令（避免覆盖为 null）
        if (!StringUtils.hasText(user.getPassword())) {
            user.setPassword(null);
        }
        user.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(user);
    }

    /**
     * 删除用户。
     *
     * @param id 用户主键 ID
     */
    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /**
     * 启用/停用用户。
     *
     * @param id      用户主键 ID
     * @param enabled 是否启用：true 启用 / false 停用
     */
    @Override
    public void toggleEnabled(Long id, boolean enabled) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setEnabled(enabled);
        u.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(u);
    }

    /**
     * 重置用户口令（口令已加密）。
     *
     * @param id              用户主键 ID
     * @param encodedPassword 已加密的口令
     */
    @Override
    public void resetPassword(Long id, String encodedPassword) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setPassword(encodedPassword);
        u.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(u);
    }
}
