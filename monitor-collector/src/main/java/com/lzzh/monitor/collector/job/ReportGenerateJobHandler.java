package com.lzzh.monitor.collector.job;

import com.lzzh.monitor.service.report.ReportService;
import jakarta.annotation.Resource;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 定时报告生成任务（§11.9 巡检与报表）。
 * <p>扫描 {@code report_schedule} 中启用且 {@code next_run <= now} 的任务，
 * 逐个生成报告归档并推进下次执行时间；单个任务失败不影响其余任务。
 * <p>由 xxl-job 调度（handler：{@code reportGenerateJobHandler}），
 * 建议 cron：{@code 0 0/10 * * * ?}（每 10 分钟扫描一次，任务粒度为日/周/月）。
 */
@Component
public class ReportGenerateJobHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerateJobHandler.class);

    @Resource
    private ReportService reportService;

    @XxlJob("reportGenerateJobHandler")
    public void execute() {
        int generated = reportService.runDueSchedules();
        String msg = "定时报告扫描完成，本轮生成 " + generated + " 份报告";
        log.info(msg);
        XxlJobHelper.log(msg);
        XxlJobHelper.handleSuccess(msg);
    }
}
