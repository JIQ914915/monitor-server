package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询持有指定角色的所有用户 ID（JSONB @> 操作符，需 GIN 索引支持）。
     * 用于角色权限变更后批量吊销 Token（P0-4）。
     *
     * @param roleCode 角色 code（如 "system_admin"）
     * @return 持有该角色的用户 ID 列表
     */
    @Select("SELECT id FROM sys_user WHERE roles @> CAST('[\"' || #{roleCode} || '\"]' AS jsonb) AND enabled = true")
    List<Long> listIdsByRoleCode(String roleCode);
}
