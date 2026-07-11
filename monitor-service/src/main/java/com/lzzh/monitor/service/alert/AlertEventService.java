package com.lzzh.monitor.service.alert;

import com.lzzh.monitor.api.request.AlertDeadLetterPageRequest;
import com.lzzh.monitor.api.request.AlertEventCountRequest;
import com.lzzh.monitor.api.request.AlertEventPageRequest;
import com.lzzh.monitor.api.response.AlertEventDrilldownVo;
import com.lzzh.monitor.api.response.AlertEventOperateLogVo;
import com.lzzh.monitor.api.response.AlertEventVo;
import com.lzzh.monitor.api.response.AlertNotifyRecordVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/**
 * 告警事件查询与处置服务。
 *
 * <p>处置状态机（单向流转，人工操作不可回退；{@code recovered} 只能由评估器/自愈机制系统触发）：
 * <pre>
 * pending ──confirm──&gt; confirmed ──close──&gt; closed
 *    │                     │
 *    └──handling──&gt; handling ──close──&gt; closed
 *                      ▲
 *                      └── confirmed 也可 handling
 *
 * {pending, confirmed, handling} ──silence──&gt; ignored
 * </pre>
 */
public interface AlertEventService {

    /**
     * 分页查询告警事件。
     *
     * <p>默认只返回未恢复（active）事件（pending / confirmed / handling）；
     * 可通过 {@code request.statuses} 传入其他状态过滤。
     *
     * @param request 分页 + 过滤条件
     * @return 分页告警事件列表
     */
    PageResult<AlertEventVo> page(AlertEventPageRequest request);

    /**
     * 统计告警事件数量。
     *
     * <p>默认只统计未恢复（active）事件（pending / confirmed / handling）；
     * 可通过 {@code request.statuses} 传入其他状态过滤。
     *
     * @param request 过滤条件
     * @return 事件总数
     */
    long count(AlertEventCountRequest request);

    /**
     * 查询单个事件的下钻分析上下文（§11.7 事件下钻）：事件基础信息 + 规则/指标元数据。
     *
     * @param eventId 事件 ID
     * @return 下钻上下文；事件不存在或不在当前用户数据范围内时抛出业务异常
     */
    AlertEventDrilldownVo drilldown(Long eventId);

    /** 批量确认事件（pending -&gt; confirmed）。 */
    int confirm(List<Long> ids, Long operatorId, String assignee, String remark);

    /** 批量受理事件（pending/confirmed -&gt; handling）。 */
    int handling(List<Long> ids, Long operatorId, String assignee, String remark);

    /** 批量静默事件（活跃状态 -&gt; ignored + 静默窗口）。 */
    int silence(List<Long> ids, Long operatorId, String assignee, Integer silenceHours, String remark);

    /** 批量关闭事件（confirmed/handling -&gt; closed）。 */
    int close(List<Long> ids, Long operatorId, String assignee, String remark);

    /** 查询事件通知记录。 */
    List<AlertNotifyRecordVo> listNotifyRecords(Long eventId);

    /** 查询事件处置操作流水（按时间倒序）。 */
    List<AlertEventOperateLogVo> listOperateLogs(Long eventId);

    /**
     * 分页查询死信通知记录（{@code status=dead}，即重试耗尽仍未送达），受数据范围约束。
     * 用于运维感知"哪些告警最终没发出去"，配合 {@link #resendDeadLetter} 手动重发。
     */
    PageResult<AlertNotifyRecordVo> pageDeadLetters(AlertDeadLetterPageRequest request);

    /**
     * 手动重发单条死信：重置为待重试态，由通知重试任务异步送达。
     *
     * @param recordId   通知记录 ID
     * @param operatorId 操作人 ID（审计用）
     * @return true 表示已成功置为待重试；false 表示记录不存在、无权限或已非死信态
     */
    boolean resendDeadLetter(Long recordId, Long operatorId);
}
