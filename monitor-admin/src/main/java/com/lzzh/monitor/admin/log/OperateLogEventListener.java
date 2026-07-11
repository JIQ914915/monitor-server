package com.lzzh.monitor.admin.log;

import com.lzzh.monitor.dao.mapper.SysOperLogMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志异步落库监听器。
 *
 * <p>使用 {@link Async} + {@link EventListener} 组合，在独立线程池
 * （{@code operateLogExecutor}，见 {@link com.lzzh.monitor.admin.config.AsyncConfig}）
 * 中异步执行 INSERT，将日志写入从 HTTP 请求线程彻底解耦：
 * <ul>
 *   <li>写入失败不影响主流程（已在此处 catch）</li>
 *   <li>线程池满时任务进入有界队列，不阻塞请求线程</li>
 * </ul>
 */
@Component
public class OperateLogEventListener {

    private static final Logger log = LoggerFactory.getLogger(OperateLogEventListener.class);

    @Resource
    private SysOperLogMapper operLogMapper;

    @Async("operateLogExecutor")
    @EventListener
    public void onOperateLog(OperateLogEvent event) {
        try {
            operLogMapper.insert(event.getOperLog());
        } catch (Exception e) {
            log.warn("异步写操作日志失败: {}", e.getMessage());
        }
    }
}
