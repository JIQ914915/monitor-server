package com.lzzh.monitor.collector.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * xxl-job 执行器注册（必选组件）。
 * <p>所有定时任务（采集/告警评估/通知重试/健康评分/保留清理）统一由 xxl-job-admin 调度，
 * 不再提供本地 @Scheduled 兜底，collector 启动前需保证 xxl-job-admin 可用。
 */
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobConfig {

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties props) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(props.getAdminAddresses());
        executor.setAccessToken(props.getAccessToken());
        executor.setAppname(props.getAppname());
        executor.setPort(props.getPort());
        executor.setLogPath(props.getLogPath());
        executor.setLogRetentionDays(props.getLogRetentionDays());
        return executor;
    }
}
