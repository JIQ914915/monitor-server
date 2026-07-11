package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.request.RetentionRequest;
import com.lzzh.monitor.api.response.RetentionVo;
import com.lzzh.monitor.dao.entity.RetentionConfig;

/** 数据保留策略实体 ↔ DTO 转换。 */
public final class RetentionConverter {

    private RetentionConverter() {
    }

    /**
     * 实体转响应 VO。
     *
     * @param e 数据保留策略实体
     * @return 数据保留策略响应 VO，入参为 null 时返回 null
     */
    public static RetentionVo toVo(RetentionConfig e) {
        if (e == null) {
            return null;
        }
        RetentionVo v = new RetentionVo();
        v.setId(e.getId());
        v.setCategory(e.getCategory());
        v.setRetentionDays(e.getRetentionDays());
        v.setEnabled(e.getEnabled());
        return v;
    }

    /**
     * 请求 DTO 转实体。
     *
     * @param r 数据保留策略请求 DTO
     * @return 数据保留策略实体，入参为 null 时返回 null
     */
    public static RetentionConfig toEntity(RetentionRequest r) {
        if (r == null) {
            return null;
        }
        RetentionConfig e = new RetentionConfig();
        e.setId(r.getId());
        e.setCategory(r.getCategory());
        e.setRetentionDays(r.getRetentionDays());
        e.setEnabled(r.getEnabled());
        return e;
    }
}
