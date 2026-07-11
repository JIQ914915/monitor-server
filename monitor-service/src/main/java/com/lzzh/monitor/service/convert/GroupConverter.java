package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.request.GroupRequest;
import com.lzzh.monitor.api.response.GroupVo;
import com.lzzh.monitor.dao.entity.InstanceGroup;

/** 实例分组实体 ↔ DTO 转换。 */
public final class GroupConverter {

    private GroupConverter() {
    }

    /**
     * 分组实体转响应 VO。
     *
     * @param e             分组实体，可为 null
     * @param instanceCount 关联实例数，由 service 层统计后传入（轻量列表场景可传 null）
     * @return 分组响应 VO；入参为 null 时返回 null
     */
    public static GroupVo toVo(InstanceGroup e, Long instanceCount) {
        if (e == null) {
            return null;
        }
        GroupVo v = new GroupVo();
        v.setId(e.getId());
        v.setName(e.getName());
        v.setParentId(e.getParentId());
        v.setOwnerId(e.getOwnerId());
        v.setMemberIds(e.getMemberIds());
        v.setDescription(e.getDescription());
        v.setInstanceCount(instanceCount);
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /**
     * 分组请求 DTO 转实体（不含时间字段）。
     *
     * @param r 分组请求 DTO，可为 null
     * @return 分组实体；入参为 null 时返回 null
     */
    public static InstanceGroup toEntity(GroupRequest r) {
        if (r == null) {
            return null;
        }
        InstanceGroup e = new InstanceGroup();
        e.setId(r.getId());
        e.setName(r.getName());
        e.setParentId(r.getParentId());
        e.setOwnerId(r.getOwnerId());
        e.setMemberIds(r.getMemberIds());
        e.setDescription(r.getDescription());
        return e;
    }
}
