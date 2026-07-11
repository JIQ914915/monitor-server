package com.lzzh.monitor.collector.runner;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzzh.monitor.api.response.HostCollectTargetVo;
import com.lzzh.monitor.collector.config.CollectProperties;
import com.lzzh.monitor.collector.host.HostCollectResult;
import com.lzzh.monitor.collector.host.HostCollector;
import com.lzzh.monitor.collector.spi.model.MetricPoint;
import com.lzzh.monitor.collector.spi.model.TextMetricPoint;
import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.dao.entity.CollectLog;
import com.lzzh.monitor.dao.entity.Host;
import com.lzzh.monitor.dao.mapper.CollectLogMapper;
import com.lzzh.monitor.dao.mapper.HostMapper;
import com.lzzh.monitor.dao.ts.TsMetricWriter;
import com.lzzh.monitor.dao.ts.TsTextWriter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 主机指标采集执行器：取本分片主机 → HostCollector 拉取解析 → 按关联实例「扇出」批量写时序。
 *
 * <p>扇出写入：主机指标以实例维度落 metric_data_1m / metric_text_data_1m（与告警评估、
 * 趋势查询、下钻共用现有链路），每个 host.* 指标点复制为该主机全部关联实例的行。
 * 未关联实例的主机跳过采集（指标无处落，管理页有提示）。
 *
 * <p>可用性联动：exporter 拉取失败写 host.availability=0（扇出），连续
 * {@value FAILURE_THRESHOLD} 次失败把 host.status 标为 abnormal；恢复后自动还原 normal。
 */
@Component
public class HostCollectRunner {

    private static final Logger log = LoggerFactory.getLogger(HostCollectRunner.class);

    private static final String AVAILABILITY_METRIC = "host.availability";
    private static final int FAILURE_THRESHOLD = 3;
    private static final String MODE_EXPORTER = "exporter";
    private static final String STATUS_PAUSED = "paused";
    private static final String STATUS_ABNORMAL = "abnormal";
    private static final String STATUS_NORMAL = "normal";

    private final HostCollector hostCollector;
    private final TsMetricWriter tsMetricWriter;
    private final TsTextWriter tsTextWriter;
    private final CollectLogMapper collectLogMapper;
    private final HostMapper hostMapper;
    private final ExecutorService pool;
    private final long hostTimeoutMs;

    /** 节点内连续拉取失败计数。 */
    private final ConcurrentHashMap<Long, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    /** 重入保护：上一轮未结束时丢弃本轮调度。 */
    private final ReentrantLock roundLock = new ReentrantLock();

    public HostCollectRunner(HostCollector hostCollector,
                             TsMetricWriter tsMetricWriter,
                             TsTextWriter tsTextWriter,
                             CollectLogMapper collectLogMapper,
                             HostMapper hostMapper,
                             CollectProperties props) {
        this.hostCollector = hostCollector;
        this.tsMetricWriter = tsMetricWriter;
        this.tsTextWriter = tsTextWriter;
        this.collectLogMapper = collectLogMapper;
        this.hostMapper = hostMapper;
        int poolSize = Math.max(1, props.getPoolSize());
        this.pool = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), r -> {
                    Thread t = new Thread(r, "host-collect-worker");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());
        this.hostTimeoutMs = props.getInstanceTimeoutMs();
    }

    public void run(List<HostCollectTargetVo> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return;
        }
        if (!roundLock.tryLock()) {
            log.warn("上一轮主机采集尚未结束，丢弃本轮调度（主机 {} 个）", hosts.size());
            return;
        }
        try {
            doRun(hosts);
        } finally {
            roundLock.unlock();
        }
    }

    private void doRun(List<HostCollectTargetVo> hosts) {
        List<HostCollectTargetVo> active = new ArrayList<>(hosts.size());
        for (HostCollectTargetVo host : hosts) {
            if (STATUS_PAUSED.equalsIgnoreCase(host.getStatus())
                    || !MODE_EXPORTER.equalsIgnoreCase(host.getCollectMode())) {
                continue;
            }
            if (host.getInstanceIds() == null || host.getInstanceIds().isEmpty()) {
                log.debug("主机 {} 未关联任何实例，跳过采集（指标无落库目标）", host.getId());
                continue;
            }
            active.add(host);
        }
        if (active.isEmpty()) {
            return;
        }
        List<Callable<Void>> tasks = active.stream()
                .<Callable<Void>>map(host -> () -> {
                    collectOne(host);
                    return null;
                })
                .toList();
        long totalTimeoutMs = hostTimeoutMs * Math.max(1, active.size());
        try {
            pool.invokeAll(tasks, totalTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void collectOne(HostCollectTargetVo host) {
        long startMs = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        int metricCount = 0, textCount = 0;
        try {
            HostCollectResult result = hostCollector.collect(
                    host.getId(), host.getIp(),
                    host.getExporterPort() == null ? 9100 : host.getExporterPort(),
                    host.getExporterPath());
            if (result.success()) {
                metricCount = result.points().size();
                textCount = result.textPoints().size();
                fanOutWrite(host, result.points(), result.textPoints());
                handleAvailability(host, true);
                if (result.hasItemErrors()) {
                    errorMessage = String.join("; ", result.itemErrors());
                    log.warn("主机 {} 采集部分失败: {}", host.getId(), errorMessage);
                } else {
                    success = true;
                }
            } else {
                errorMessage = result.error();
                handleAvailability(host, false);
                log.warn("主机 {} 采集失败: {}", host.getId(), errorMessage);
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            handleAvailability(host, false);
            log.error("主机 {} 采集异常", host.getId(), e);
        } finally {
            writeCollectLog(host.getId(), startMs, success, metricCount, textCount, errorMessage);
        }
    }

    /** 扇出写入：主机维度指标点复制为全部关联实例的行（数值 + 文本）。 */
    private void fanOutWrite(HostCollectTargetVo host, List<MetricPoint> points, List<TextMetricPoint> textPoints) {
        List<Long> instanceIds = host.getInstanceIds();
        if (instanceIds == null || instanceIds.isEmpty()) {
            return;
        }
        if (points != null && !points.isEmpty()) {
            List<TsMetricWriter.TsMetricPoint> rows = new ArrayList<>(points.size() * instanceIds.size());
            for (Long instanceId : instanceIds) {
                for (MetricPoint p : points) {
                    rows.add(new TsMetricWriter.TsMetricPoint(instanceId, p.metric(), p.value(), p.timestampMillis()));
                }
            }
            tsMetricWriter.batchWrite(CollectFrequency.MINUTE, rows);
        }
        if (textPoints != null && !textPoints.isEmpty()) {
            List<TsTextWriter.TsTextPoint> rows = new ArrayList<>(textPoints.size() * instanceIds.size());
            for (Long instanceId : instanceIds) {
                for (TextMetricPoint p : textPoints) {
                    rows.add(new TsTextWriter.TsTextPoint(instanceId, p.metric(), p.valueText(), p.valueHash(), p.timestampMillis()));
                }
            }
            tsTextWriter.batchWrite(CollectFrequency.MINUTE, rows);
        }
    }

    /**
     * exporter 可达性与主机状态联动：
     * 失败写 host.availability=0（扇出，供 builtin.host.unreachable 规则评估），
     * 连续 {@value FAILURE_THRESHOLD} 次失败标 abnormal；成功写 1 并还原 normal。
     */
    private void handleAvailability(HostCollectTargetVo host, boolean reachable) {
        long ts = System.currentTimeMillis();
        fanOutWrite(host, List.of(new MetricPoint(AVAILABILITY_METRIC, reachable ? 1.0 : 0.0, ts)), null);
        Long hostId = host.getId();
        if (reachable) {
            consecutiveFailures.remove(hostId);
            try {
                int updated = hostMapper.update(null, new LambdaUpdateWrapper<Host>()
                        .eq(Host::getId, hostId)
                        .eq(Host::getStatus, STATUS_ABNORMAL)
                        .set(Host::getStatus, STATUS_NORMAL));
                if (updated > 0) {
                    log.info("主机 {} exporter 已恢复可达，status → normal", hostId);
                }
            } catch (Exception e) {
                log.warn("恢复主机 {} status 失败", hostId, e);
            }
        } else {
            // 长期不可达时清理 delta 基线，避免恢复后首个差值跨越故障窗口失真
            hostCollector.evictBaseline(hostId);
            int fails = consecutiveFailures
                    .computeIfAbsent(hostId, k -> new AtomicInteger(0))
                    .incrementAndGet();
            if (fails >= FAILURE_THRESHOLD) {
                try {
                    int updated = hostMapper.update(null, new LambdaUpdateWrapper<Host>()
                            .eq(Host::getId, hostId)
                            .notIn(Host::getStatus, STATUS_ABNORMAL, STATUS_PAUSED)
                            .set(Host::getStatus, STATUS_ABNORMAL));
                    if (updated > 0) {
                        log.warn("主机 {} 连续 {} 次拉取失败，status → abnormal", hostId, fails);
                    }
                } catch (Exception e) {
                    log.warn("标记主机 {} abnormal 失败", hostId, e);
                }
            }
        }
    }

    /** 主机采集日志：host_id 填主机，instance_id 填 0（collect_log.instance_id NOT NULL）。 */
    private void writeCollectLog(Long hostId, long startMs, boolean success,
                                 int metricCount, int textCount, String errorMessage) {
        try {
            CollectLog entry = new CollectLog();
            entry.setInstanceId(0L);
            entry.setHostId(hostId);
            entry.setFrequency("1m");
            entry.setCollectTime(OffsetDateTime.now());
            entry.setDurationMs((int) (System.currentTimeMillis() - startMs));
            entry.setSuccess(success);
            entry.setMetricCount(metricCount);
            entry.setTextCount(textCount);
            entry.setObjectCount(0);
            if (errorMessage != null && errorMessage.length() > 2000) {
                errorMessage = errorMessage.substring(0, 2000);
            }
            entry.setErrorMessage(errorMessage);
            collectLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("写主机采集日志失败 host={}", hostId, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
