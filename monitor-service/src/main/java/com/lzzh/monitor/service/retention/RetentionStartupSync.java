package com.lzzh.monitor.service.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import jakarta.annotation.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动后把 retention_config 现有配置同步为真实 TimescaleDB 保留策略，
 * 使「重启后 DB 策略 == 配置」，避免迁移里硬编码的默认值与配置漂移。
 * 下发幂等，失败不影响启动。
 */
@Component
@Order(100)
public class RetentionStartupSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetentionStartupSync.class);

    @Resource
    private RetentionService retentionService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            retentionService.syncPoliciesFromConfig();
        } catch (Exception e) {
            log.warn("启动同步保留策略失败（不影响启动）: {}", e.getMessage());
        }
    }
}
