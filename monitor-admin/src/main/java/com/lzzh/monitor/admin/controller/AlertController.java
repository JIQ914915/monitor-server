package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.AlertDeadLetterPageRequest;
import com.lzzh.monitor.api.request.AlertEventCountRequest;
import com.lzzh.monitor.api.request.AlertEventBatchRequest;
import com.lzzh.monitor.api.request.AlertEventPageRequest;
import com.lzzh.monitor.api.response.AlertEventDrilldownVo;
import com.lzzh.monitor.api.response.AlertEventOperateLogVo;
import com.lzzh.monitor.api.response.AlertEventVo;
import com.lzzh.monitor.api.response.AlertNotifyRecordVo;
import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.admin.security.SecurityUtils;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.alert.AlertEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警管理 API。
 * <p>告警事件查询（实例告警事件 Tab）。
 */
@Tag(name = "告警管理", description = "告警事件查询与处置")
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertEventService alertEventService;

    public AlertController(AlertEventService alertEventService) {
        this.alertEventService = alertEventService;
    }

    /**
     * 分页查询告警事件。
     *
     * <p>不传 statuses 时默认返回未恢复事件（pending / confirmed / handling）；
     * 可传 statuses=["recovered","closed"] 查看历史事件。
     *
     * <p>常见用法：实例告警事件 Tab 传 instanceId，不传 statuses（活跃事件）。
     *
     * @param req 分页查询请求
     */
    @Operation(
            summary = "分页查询告警事件",
            description = "支持按实例/规则级别/状态过滤，默认返回所有未恢复（活跃）事件"
    )
    @PostMapping("/events/page")
    @RequiresPerm("alert_rule:view")
    public Result<PageResult<AlertEventVo>> page(@RequestBody(required = false) AlertEventPageRequest req) {
        return Result.ok(alertEventService.page(req == null ? new AlertEventPageRequest() : req));
    }

    @Operation(
            summary = "统计告警事件数量",
            description = "支持按实例/规则级别/状态过滤，仅返回总数，适用于轻量统计场景"
    )
    @PostMapping("/events/count")
    @RequiresPerm("alert_rule:view")
    public Result<Long> count(@RequestBody(required = false) AlertEventCountRequest req) {
        return Result.ok(alertEventService.count(req == null ? new AlertEventCountRequest() : req));
    }

    @Operation(
            summary = "查询告警事件下钻分析上下文",
            description = "事件基础信息 + 规则/指标元数据（指标编码/中文名/单位/比较符/恢复时间），"
                    + "前端据此拉取告警前后趋势并匹配告警类型画像（§11.7 事件下钻）"
    )
    @PostMapping("/events/{eventId}/drilldown")
    @RequiresPerm("alert_rule:view")
    public Result<AlertEventDrilldownVo> drilldown(@PathVariable Long eventId) {
        return Result.ok(alertEventService.drilldown(eventId));
    }

    @Operation(summary = "查询告警事件通知记录", description = "按事件 ID 查询 Webhook/邮件/短信发送记录与失败原因")
    @PostMapping("/events/{eventId}/notify-records")
    @RequiresPerm("alert_rule:view")
    public Result<List<AlertNotifyRecordVo>> notifyRecords(@PathVariable Long eventId) {
        return Result.ok(alertEventService.listNotifyRecords(eventId));
    }

    @Operation(summary = "查询告警事件处置流水", description = "按事件 ID 查询确认/受理/静默/关闭等人工处置操作历史")
    @PostMapping("/events/{eventId}/operate-logs")
    @RequiresPerm("alert_rule:view")
    public Result<List<AlertEventOperateLogVo>> operateLogs(@PathVariable Long eventId) {
        return Result.ok(alertEventService.listOperateLogs(eventId));
    }

    @Operation(
            summary = "分页查询死信通知",
            description = "查询重试耗尽仍未送达（dead）的通知记录，用于运维感知发送失败的告警"
    )
    @PostMapping("/notify-records/dead/page")
    @RequiresPerm("alert_rule:view")
    public Result<PageResult<AlertNotifyRecordVo>> deadLetters(
            @RequestBody(required = false) AlertDeadLetterPageRequest req) {
        return Result.ok(alertEventService.pageDeadLetters(req == null ? new AlertDeadLetterPageRequest() : req));
    }

    @Operation(summary = "手动重发死信通知", description = "将死信记录重置为待重试，由通知重试任务异步重新发送")
    @PostMapping("/notify-records/{recordId}/resend")
    @RequiresPerm("alert_rule:edit")
    public Result<Boolean> resendNotify(@PathVariable Long recordId) {
        var u = SecurityUtils.current();
        return Result.ok(alertEventService.resendDeadLetter(recordId, u.id()));
    }

    @Operation(
            summary = "批量受理告警事件",
            description = "将待处理/已确认事件标记为处理中（单向流转：pending/confirmed -> handling）"
    )
    @PostMapping("/events/handling")
    @RequiresPerm("alert_rule:edit")
    public Result<Integer> handling(@Valid @RequestBody AlertEventBatchRequest req) {
        var u = SecurityUtils.current();
        return Result.ok(alertEventService.handling(req.getIds(), u.id(), u.username(), req.getRemark()));
    }

    @Operation(
            summary = "批量确认告警事件",
            description = "将待处理事件标记为已确认（单向流转：仅 pending -> confirmed，处理中事件请用受理/关闭）"
    )
    @PostMapping("/events/confirm")
    @RequiresPerm("alert_rule:edit")
    public Result<Integer> confirm(@Valid @RequestBody AlertEventBatchRequest req) {
        var u = SecurityUtils.current();
        return Result.ok(alertEventService.confirm(req.getIds(), u.id(), u.username(), req.getRemark()));
    }

    @Operation(summary = "批量静默告警事件", description = "将活跃事件标记为已静默（ignored），并设置静默窗口（小时）")
    @PostMapping("/events/silence")
    @RequiresPerm("alert_rule:edit")
    public Result<Integer> silence(@Valid @RequestBody AlertEventBatchRequest req) {
        var u = SecurityUtils.current();
        return Result.ok(alertEventService.silence(
                req.getIds(), u.id(), u.username(), req.getSilenceHours(), req.getRemark()));
    }

    @Operation(
            summary = "批量关闭告警事件",
            description = "将已确认/处理中的事件标记为已关闭（单向流转：confirmed/handling -> closed）"
    )
    @PostMapping("/events/close")
    @RequiresPerm("alert_rule:edit")
    public Result<Integer> close(@Valid @RequestBody AlertEventBatchRequest req) {
        var u = SecurityUtils.current();
        return Result.ok(alertEventService.close(req.getIds(), u.id(), u.username(), req.getRemark()));
    }
}
