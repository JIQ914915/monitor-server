package com.lzzh.monitor.collector.alert;

/** 评估统计计数（{@code notified} 为"已派发"口径，语义见 {@link AlertUpsertResult}）。 */
public record AlertEvalCounter(int triggered, int silenced, int notified, int recovered) {
    public static final AlertEvalCounter EMPTY = new AlertEvalCounter(0, 0, 0, 0);

    public AlertEvalCounter addTriggered(boolean notifiedNow) {
        return new AlertEvalCounter(triggered + 1, silenced, notified + (notifiedNow ? 1 : 0), recovered);
    }

    public AlertEvalCounter addSilenced() {
        return new AlertEvalCounter(triggered, silenced + 1, notified, recovered);
    }

    public AlertEvalCounter addRecovered() {
        return new AlertEvalCounter(triggered, silenced, notified, recovered + 1);
    }
}
