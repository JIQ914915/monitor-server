package com.lzzh.monitor.collector.alert;

import com.lzzh.monitor.collector.config.AlertNotifyProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警风暴抑制守卫（按实例维度，进程内内存态）。
 *
 * <p>当同一实例在窗口内的触发通知数超过阈值时，抑制后续逐条通知，
 * 并按最小间隔发送一条聚合摘要，避免大量规则同时触发造成通知轰炸。{@code threshold<=0} 时关闭抑制（始终放行）。
 *
 * <p>xxl-job 分片广播下，同一实例的评估固定落在一个 collector 节点上，
 * 进程内窗口计数即可保证抑制语义，无需跨节点共享状态。
 * 进程内态附带过期清理，避免已下线实例的窗口状态无限驻留造成内存泄漏。
 */
@Component
public class AlertStormGuard {

    /** 进程内窗口清理最小间隔（毫秒），避免每次判定都全量遍历。 */
    private static final long SWEEP_INTERVAL_MS = 60_000L;

    public enum Kind {
        /** 放行，正常逐条通知。 */
        ALLOW,
        /** 抑制逐条通知，改为发送一条聚合摘要。 */
        DIGEST,
        /** 抑制且不发送（摘要间隔内）。 */
        SUPPRESS
    }

    /** 判定结果。{@code suppressedCount} 为自上次摘要以来被抑制的通知数（含本次），供摘要文案使用。 */
    public record Decision(Kind kind, int suppressedCount) {
        static final Decision ALLOW = new Decision(Kind.ALLOW, 0);
    }

    private final AlertNotifyProperties properties;
    private final ConcurrentHashMap<Long, InstanceWindow> windows = new ConcurrentHashMap<>();
    private volatile long lastSweepMs = 0L;

    public AlertStormGuard(AlertNotifyProperties properties) {
        this.properties = properties;
    }

    /** 判定一次触发通知是否放行/抑制/发摘要，并推进内部窗口计数。 */
    public Decision decide(Long instanceId) {
        AlertNotifyProperties.Storm storm = properties.getStorm();
        int threshold = storm.getThreshold();
        if (threshold <= 0 || instanceId == null) {
            return Decision.ALLOW;
        }
        int windowMinutes = Math.max(1, storm.getWindowMinutes());
        int digestIntervalMinutes = Math.max(1, storm.getDigestIntervalMinutes());
        long now = System.currentTimeMillis();
        long windowMs = windowMinutes * 60_000L;
        long digestIntervalMs = digestIntervalMinutes * 60_000L;
        sweepStaleWindows(now, windowMs, digestIntervalMs);
        InstanceWindow w = windows.computeIfAbsent(instanceId, k -> new InstanceWindow());
        synchronized (w) {
            w.lastActivityMs = now;
            while (!w.allowedTimes.isEmpty() && now - w.allowedTimes.peekFirst() > windowMs) {
                w.allowedTimes.pollFirst();
            }
            if (w.allowedTimes.size() < threshold) {
                w.allowedTimes.addLast(now);
                return Decision.ALLOW;
            }
            w.suppressedSinceDigest++;
            if (now - w.lastDigestMs >= digestIntervalMs) {
                int suppressed = w.suppressedSinceDigest;
                w.suppressedSinceDigest = 0;
                w.lastDigestMs = now;
                return new Decision(Kind.DIGEST, suppressed);
            }
            return new Decision(Kind.SUPPRESS, w.suppressedSinceDigest);
        }
    }

    /**
     * 清理长期无活动的实例窗口，防止已下线实例的状态在进程内无限驻留（内存泄漏）。
     * 仅在窗口计数已清空且超过保留期时移除，避免误删仍在抑制期内的实例。
     */
    private void sweepStaleWindows(long now, long windowMs, long digestIntervalMs) {
        if (now - lastSweepMs < SWEEP_INTERVAL_MS) {
            return;
        }
        lastSweepMs = now;
        long retentionMs = windowMs + digestIntervalMs + SWEEP_INTERVAL_MS;
        windows.entrySet().removeIf(entry -> {
            InstanceWindow w = entry.getValue();
            synchronized (w) {
                return w.allowedTimes.isEmpty() && (now - w.lastActivityMs) > retentionMs;
            }
        });
    }

    private static final class InstanceWindow {
        private final Deque<Long> allowedTimes = new ArrayDeque<>();
        private long lastDigestMs = 0L;
        private int suppressedSinceDigest = 0;
        private long lastActivityMs = System.currentTimeMillis();
    }
}
