package com.lzzh.monitor.collector.job;

import com.lzzh.monitor.collector.alert.AlertNotificationService;
import jakarta.annotation.Resource;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 告警通知失败重试任务。 */
@Component
public class AlertNotifyRetryJobHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertNotifyRetryJobHandler.class);

    @Resource
    private AlertNotificationService alertNotificationService;

    @XxlJob("alertNotifyRetryJobHandler")
    public void retry() {
        int success = alertNotificationService.retryFailed();
        int cleaned = alertNotificationService.cleanupRetired();
        XxlJobHelper.log("告警通知重试完成，成功 {} 条，清理过期记录 {} 条", success, cleaned);
        log.info("告警通知重试完成，成功 {} 条，清理过期记录 {} 条", success, cleaned);
    }
}
