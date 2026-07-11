package com.lzzh.monitor.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** 节点内采集并发配置（§14.1.3 固定线程池 + 超时取消）。 */
@Data
@ConfigurationProperties(prefix = "collector.concurrency")
public class CollectProperties {

    /** 采集线程池大小：单节点同时采集（即同时访问目标库）的最大实例数。 */
    private int poolSize = 8;

    /** 单实例采集整体超时(毫秒)：整轮预算 = 该值 × 批次数，超时后未完成任务统一取消。分钟级适用。 */
    private long instanceTimeoutMs = 15000;

    /**
     * 小时级单实例超时(毫秒)。小时级包含 information_schema.tables、表 I/O 差值等重查询，
     * 远程实例常见 20~50s，沿用分钟级 15s 预算会被整轮取消——任务被 cancel(true) 打上中断标记后，
     * 后续 Druid 取连接对中断敏感，落库直接失败（表现为 CannotGetJdbcConnectionException）。
     */
    private long hourlyInstanceTimeoutMs = 120_000;

    /** 天级单实例超时(毫秒)：未使用索引扫描、参数快照等低频重查询，预算更宽。 */
    private long dailyInstanceTimeoutMs = 180_000;

    /** 按采集频率返回单实例超时预算。 */
    public long timeoutMsFor(com.lzzh.monitor.common.enums.CollectFrequency frequency) {
        if (frequency == com.lzzh.monitor.common.enums.CollectFrequency.HOURLY) {
            return hourlyInstanceTimeoutMs;
        }
        if (frequency == com.lzzh.monitor.common.enums.CollectFrequency.DAILY) {
            return dailyInstanceTimeoutMs;
        }
        return instanceTimeoutMs;
    }
}
