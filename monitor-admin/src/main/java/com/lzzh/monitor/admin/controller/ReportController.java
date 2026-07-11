package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.EnabledRequest;
import com.lzzh.monitor.api.request.ReportGenerateRequest;
import com.lzzh.monitor.api.request.ReportPageRequest;
import com.lzzh.monitor.api.request.ReportScheduleSaveRequest;
import com.lzzh.monitor.api.response.ReportDetailVo;
import com.lzzh.monitor.api.response.ReportScheduleVo;
import com.lzzh.monitor.api.response.ReportVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 报告中心（§11.9 巡检与报表）：报告归档、生成、定时任务管理。 */
@Tag(name = "报告中心", description = "巡检/性能/告警三类报告的生成、归档、预览与定时任务管理")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    @Resource
    private ReportService reportService;

    /**
     * 报告归档分页查询。
     *
     * @param req 分页与类型过滤条件
     * @return 报告归档分页结果（按生成时间倒序）
     */
    @Operation(summary = "报告归档分页", description = "按生成时间倒序，支持标题关键字与报告类型过滤")
    @PostMapping("/page")
    @RequiresPerm("report:view")
    public Result<PageResult<ReportVo>> page(@RequestBody ReportPageRequest req) {
        return Result.ok(reportService.page(req));
    }

    /**
     * 报告详情（含分段正文）。
     *
     * @param req 报告主键
     * @return 报告详情
     */
    @Operation(summary = "报告详情", description = "返回报告元数据与分段正文（sections），供预览页渲染与导出")
    @PostMapping("/detail")
    @RequiresPerm("report:view")
    public Result<ReportDetailVo> detail(@Valid @RequestBody IdRequest req) {
        return Result.ok(reportService.detail(req.getId()));
    }

    /**
     * 立即生成报告并归档。
     *
     * @param req 报告类型、范围与时间窗口
     * @return 归档报告主键 ID
     */
    @Operation(summary = "生成报告", description = "按真实监控数据生成巡检/性能/告警报告并归档，实例范围经数据范围校验")
    @PostMapping("/generate")
    @RequiresPerm("report:create")
    @OperateLog(module = "报告中心", action = "生成报告")
    public Result<Long> generate(@Valid @RequestBody ReportGenerateRequest req) {
        return Result.ok(reportService.generate(req));
    }

    /**
     * 删除归档报告。
     *
     * @param req 报告主键
     * @return 空响应体
     */
    @Operation(summary = "删除报告", description = "删除归档报告（不可恢复）")
    @PostMapping("/delete")
    @RequiresPerm("report:delete")
    @OperateLog(module = "报告中心", action = "删除报告")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        reportService.delete(req.getId());
        return Result.ok();
    }

    /**
     * 定时任务列表。
     *
     * @return 定时报告任务列表（按创建时间倒序）
     */
    @Operation(summary = "定时任务列表", description = "定时报告任务数量少，一次性返回全部")
    @PostMapping("/schedules/list")
    @RequiresPerm("report:view")
    public Result<List<ReportScheduleVo>> schedules() {
        return Result.ok(reportService.schedules());
    }

    /**
     * 新增/更新定时任务。
     *
     * @param req 任务配置（id 为空新增）
     * @return 任务主键 ID
     */
    @Operation(summary = "保存定时任务", description = "id 为空新增；保存后按频率与执行时间计算下次执行时间")
    @PostMapping("/schedules/save")
    @RequiresPerm("report:schedule")
    @OperateLog(module = "报告中心", action = "保存定时任务")
    public Result<Long> saveSchedule(@Valid @RequestBody ReportScheduleSaveRequest req) {
        return Result.ok(reportService.saveSchedule(req));
    }

    /**
     * 启停定时任务。
     *
     * @param req 主键与启停状态
     * @return 空响应体
     */
    @Operation(summary = "启停定时任务", description = "启用时重算下次执行时间")
    @PostMapping("/schedules/toggle")
    @RequiresPerm("report:schedule")
    @OperateLog(module = "报告中心", action = "启停定时任务")
    public Result<Void> toggleSchedule(@Valid @RequestBody EnabledRequest req) {
        reportService.toggleSchedule(req.getId(), Boolean.TRUE.equals(req.getEnabled()));
        return Result.ok();
    }

    /**
     * 删除定时任务。
     *
     * @param req 任务主键
     * @return 空响应体
     */
    @Operation(summary = "删除定时任务", description = "删除后不再定时生成，已归档报告不受影响")
    @PostMapping("/schedules/delete")
    @RequiresPerm("report:schedule")
    @OperateLog(module = "报告中心", action = "删除定时任务")
    public Result<Void> deleteSchedule(@Valid @RequestBody IdRequest req) {
        reportService.deleteSchedule(req.getId());
        return Result.ok();
    }

    /**
     * 立即执行一次定时任务。
     *
     * @param req 任务主键
     * @return 生成的报告主键 ID
     */
    @Operation(summary = "立即执行定时任务", description = "手动触发一次报告生成归档，不影响下次执行时间计划")
    @PostMapping("/schedules/run-now")
    @RequiresPerm("report:schedule")
    @OperateLog(module = "报告中心", action = "立即执行定时任务")
    public Result<Long> runScheduleNow(@Valid @RequestBody IdRequest req) {
        return Result.ok(reportService.runScheduleNow(req.getId()));
    }
}
