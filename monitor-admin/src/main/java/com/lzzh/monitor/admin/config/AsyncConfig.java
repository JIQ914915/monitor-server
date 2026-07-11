package com.lzzh.monitor.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * <p>为操作日志写入提供独立线程池 {@code operateLogExecutor}，
 * 与业务请求线程池隔离，避免日志写入影响主业务性能，也防止日志
 * 积压时反向拖慢核心接口。
 *
 * <p>参数说明：
 * <ul>
 *   <li>核心线程数 2：日志量通常不大，2 条线程足以消化正常流量</li>
 *   <li>最大线程数 4：突发流量时允许扩展</li>
 *   <li>队列容量 500：有界队列，防止 OOM；超出后触发 CallerRunsPolicy
 *       回退到调用方线程（降级为同步写入）而非直接丢弃</li>
 * </ul>
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("operateLogExecutor")
    public Executor operateLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("oper-log-");
        executor.setKeepAliveSeconds(60);
        // 队列满时回退到调用方线程同步执行，保证日志不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
