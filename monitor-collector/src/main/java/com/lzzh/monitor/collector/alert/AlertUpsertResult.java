package com.lzzh.monitor.collector.alert;

/**
 * 事件建单/更新结果。
 * <p>{@code TRIGGERED_AND_NOTIFIED} 中的"NOTIFIED"是"已派发通知"（notifyOnTrigger 返回 true：
 * 通知记录已落库并提交异步发送/重试队列），不代表下游已确认送达——真实送达结果以
 * {@code alert_notify_record.status} 为准，详见 {@link AlertNotificationService#notifyOnTrigger}。
 */
public enum AlertUpsertResult {
    WINDOW_PENDING,
    SILENCE_SUPPRESSED,
    /** 事件在评估期间被人工终态处置（或并发建单竞态落空），本轮跳过，不计入任何统计。 */
    DISPOSED_SKIPPED,
    TRIGGERED_ONLY,
    TRIGGERED_AND_NOTIFIED;

    public boolean windowPending() {
        return this == WINDOW_PENDING;
    }

    public boolean silenceSuppressed() {
        return this == SILENCE_SUPPRESSED;
    }

    public boolean disposedSkipped() {
        return this == DISPOSED_SKIPPED;
    }

    public boolean notified() {
        return this == TRIGGERED_AND_NOTIFIED;
    }
}
