package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.response.OperLogVo;
import com.lzzh.monitor.dao.entity.SysOperLog;

/** 操作日志实体 → DTO 转换（只读审计）。 */
public final class OperLogConverter {

    private OperLogConverter() {
    }

    /**
     * 实体转响应 VO。
     *
     * @param e 操作日志实体
     * @return 操作日志响应 VO，入参为 null 时返回 null
     */
    public static OperLogVo toVo(SysOperLog e) {
        if (e == null) {
            return null;
        }
        OperLogVo v = new OperLogVo();
        v.setId(e.getId());
        v.setOperTime(e.getOperTime());
        v.setUsername(e.getUsername());
        v.setModule(e.getModule());
        v.setAction(e.getAction());
        v.setTarget(e.getTarget());
        v.setIp(e.getIp());
        v.setSuccess(e.getSuccess());
        v.setDetail(e.getDetail());
        return v;
    }
}
