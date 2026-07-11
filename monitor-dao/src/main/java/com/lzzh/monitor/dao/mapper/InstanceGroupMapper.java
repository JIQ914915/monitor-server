package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.InstanceGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InstanceGroupMapper extends BaseMapper<InstanceGroup> {

    /**
     * 查询指定用户作为负责人或成员所属的分组 ID 集合，数据范围校验组件依据之一。
     *
     * @param userId 用户ID
     * @return 该用户所属（负责人或成员）的分组 ID 列表
     */
    @Select("SELECT id FROM instance_group WHERE owner_id = #{userId} "
            + "OR member_ids @> CONCAT('[', #{userId}, ']')::jsonb")
    List<Long> selectGroupIdsForUser(@Param("userId") Long userId);
}
