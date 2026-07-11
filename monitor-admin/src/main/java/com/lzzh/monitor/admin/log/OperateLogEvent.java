package com.lzzh.monitor.admin.log;

import com.lzzh.monitor.dao.entity.SysOperLog;
import org.springframework.context.ApplicationEvent;

/**
 * 操作日志领域事件：由 {@link OperateLogAspect} 发布，
 * 由 {@link OperateLogEventListener} 异步消费并落库。
 * 将日志写入从请求线程解耦，避免同步 DB 写影响接口响应时间。
 */
public class OperateLogEvent extends ApplicationEvent {

    private final SysOperLog operLog;

    public OperateLogEvent(Object source, SysOperLog operLog) {
        super(source);
        this.operLog = operLog;
    }

    public SysOperLog getOperLog() {
        return operLog;
    }
}
