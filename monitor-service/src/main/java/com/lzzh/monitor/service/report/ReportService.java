package com.lzzh.monitor.service.report;

import com.lzzh.monitor.api.request.ReportGenerateRequest;
import com.lzzh.monitor.api.request.ReportPageRequest;
import com.lzzh.monitor.api.request.ReportScheduleSaveRequest;
import com.lzzh.monitor.api.response.ReportDetailVo;
import com.lzzh.monitor.api.response.ReportScheduleVo;
import com.lzzh.monitor.api.response.ReportVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/**
 * 报告中心（§11.9 巡检与报表）：巡检/性能/告警三类报告的生成、归档与定时任务管理。
 * <p>报告内容按真实监控数据分段生成后 JSONB 落库归档，前端预览页分段渲染，
 * 支持打印与导出 Word；定时任务由 xxl-job {@code reportGenerateJobHandler} 扫描执行。
 */
public interface ReportService {

    /** 报告归档分页查询（按生成时间倒序）。 */
    PageResult<ReportVo> page(ReportPageRequest req);

    /** 报告详情（含分段正文）。 */
    ReportDetailVo detail(Long id);

    /** 立即生成报告并归档；实例范围经数据范围校验。返回归档报告 ID。 */
    Long generate(ReportGenerateRequest req);

    /** 删除归档报告。 */
    void delete(Long id);

    /** 定时任务列表（按创建时间倒序）。 */
    List<ReportScheduleVo> schedules();

    /** 新增/更新定时任务（id 为空新增），并计算下次执行时间。 */
    Long saveSchedule(ReportScheduleSaveRequest req);

    /** 启停定时任务；启用时重算下次执行时间。 */
    void toggleSchedule(Long id, boolean enabled);

    /** 删除定时任务。 */
    void deleteSchedule(Long id);

    /** 立即执行一次定时任务（生成报告归档，不影响下次执行时间计划）。返回报告 ID。 */
    Long runScheduleNow(Long id);

    /** 调度入口：扫描 next_run 到期的启用任务生成报告并推进 next_run。返回生成报告数。 */
    int runDueSchedules();
}
