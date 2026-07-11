package com.lzzh.monitor.collector;

import com.lzzh.monitor.collector.config.CollectProperties;
import com.lzzh.monitor.collector.config.AlertNotifyProperties;
import com.lzzh.monitor.common.security.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 采集执行器启动类（xxl-job Executor）。
 * 扫描整个产品包：聚合 collector-spi 抽象 + 各 collector-&lt;db&gt; 实现（自动注册到 CollectorRegistry）。
 * <p>业务定时任务统一由 xxl-job 调度；@EnableScheduling 仅用于进程内维护任务
 * （目标库连接健康检查等 @Scheduled）。
 */
@SpringBootApplication(scanBasePackages = "com.lzzh.monitor")
@MapperScan("com.lzzh.monitor.dao.mapper")
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, CollectProperties.class, AlertNotifyProperties.class})
public class CollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectorApplication.class, args);
    }
}
