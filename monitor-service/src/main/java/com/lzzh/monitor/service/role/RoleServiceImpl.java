package com.lzzh.monitor.service.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.RolePageRequest;
import com.lzzh.monitor.api.request.RoleRequest;
import com.lzzh.monitor.api.response.RoleVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.SysRole;
import com.lzzh.monitor.dao.entity.SysUser;
import com.lzzh.monitor.dao.mapper.SysRoleMapper;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.service.convert.RoleConverter;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private SysRoleMapper mapper;
    @Resource
    private SysUserMapper userMapper;

    /**
     * 分页查询角色，并补充每个角色的关联用户数。
     *
     * @param param 分页与过滤条件（关键字/类型/状态）
     * @return 角色分页结果
     */
    @Override
    public PageResult<RoleVo> page(RolePageRequest param) {
        Page<SysRole> page = Pages.build(param);
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        if (param != null && StringUtils.hasText(param.getKeyword())) {
            qw.and(w -> w.like(SysRole::getName, param.getKeyword())
                    .or().like(SysRole::getCode, param.getKeyword()));
        }
        if (param != null && StringUtils.hasText(param.getType())) {
            qw.eq(SysRole::getType, param.getType());
        }
        if (param != null && StringUtils.hasText(param.getStatus())) {
            qw.eq(SysRole::getStatus, param.getStatus());
        }
        qw.orderByDesc(SysRole::getId);
        PageResult<SysRole> result = Pages.toResult(mapper.selectPage(page, qw));
        return result.map(r -> RoleConverter.toVo(r, countUsers(r.getCode())));
    }

    /**
     * 查询全部角色（按 ID 升序）。
     *
     * @return 全部角色列表
     */
    @Override
    public List<RoleVo> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId))
                .stream().map(r -> RoleConverter.toVo(r, null)).toList();
    }

    /**
     * 新增角色，校验角色编码唯一并填充默认类型/状态与时间。
     *
     * @param request 角色信息及权限配置
     * @return 新建角色的主键 ID
     */
    @Override
    public Long create(RoleRequest request) {
        SysRole role = RoleConverter.toEntity(request);
        Long count = mapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, role.getCode()));
        if (count != null && count > 0) {
            throw new BusinessException("角色编码已存在: " + role.getCode());
        }
        if (!StringUtils.hasText(role.getType())) {
            role.setType("custom");
        }
        if (!StringUtils.hasText(role.getStatus())) {
            role.setStatus("enabled");
        }
        if (!StringUtils.hasText(role.getDataScope())) {
            // 安全默认：新建角色默认"仅本人负责"，需管理员显式放宽为全部/指定分组
            role.setDataScope("self");
        }
        role.setCreateTime(OffsetDateTime.now());
        role.setUpdateTime(OffsetDateTime.now());
        mapper.insert(role);
        return role.getId();
    }

    /**
     * 修改角色并刷新更新时间。
     *
     * @param request 角色信息及权限配置（须含主键 ID）
     */
    @Override
    public void update(RoleRequest request) {
        SysRole role = RoleConverter.toEntity(request);
        role.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(role);
    }

    /**
     * 删除角色，预设角色不可删除。
     *
     * @param id 角色主键 ID
     */
    @Override
    public void delete(Long id) {
        SysRole role = mapper.selectById(id);
        if (role != null && "preset".equals(role.getType())) {
            throw new BusinessException("预设角色不可删除: " + role.getName());
        }
        mapper.deleteById(id);
    }

    /**
     * 启用/停用角色并刷新更新时间。
     *
     * @param id     角色主键 ID
     * @param status 目标状态：enabled 启用 / disabled 停用
     */
    @Override
    public void toggleStatus(Long id, String status) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setStatus(status);
        role.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(role);
    }

    /** 统计 sys_user.roles 中包含该角色编码的用户数（PG jsonb 包含查询）。 */
    private Long countUsers(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return 0L;
        }
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        qw.apply("roles @> {0}::jsonb", "[\"" + roleCode.replace("\"", "") + "\"]");
        return userMapper.selectCount(qw);
    }
}
