package com.lzzh.monitor.collector.runner;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.collector.alert.ConnectionFailureAlertService;
import com.lzzh.monitor.collector.connection.TargetDataSourceFactory;
import com.lzzh.monitor.collector.config.CollectProperties;
import com.lzzh.monitor.collector.spi.CollectorFactory;
import com.lzzh.monitor.collector.spi.DatabaseCollector;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.CollectResult;
import com.lzzh.monitor.collector.spi.model.LongConnPoint;
import com.lzzh.monitor.collector.spi.model.MetricPoint;
import com.lzzh.monitor.collector.spi.model.ObjectMetricPoint;
import com.lzzh.monitor.collector.spi.model.PgQueryStatPoint;
import com.lzzh.monitor.collector.spi.model.SlowSqlSamplePoint;
import com.lzzh.monitor.collector.spi.model.TextMetricPoint;
import com.lzzh.monitor.collector.spi.model.TopSqlPoint;
import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.enums.InstanceStatus;
import com.lzzh.monitor.dao.entity.CollectLog;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.CollectLogMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.ts.TsCapacityObjectWriter;
import com.lzzh.monitor.dao.ts.TsLongConnWriter;
import com.lzzh.monitor.dao.ts.TsMetricWriter;
import com.lzzh.monitor.dao.ts.TsParamQueryDao;
import com.lzzh.monitor.dao.ts.TsPgQueryStatWriter;
import com.lzzh.monitor.dao.ts.TsSlowSqlSampleWriter;
import com.lzzh.monitor.dao.ts.TsTextWriter;
import com.lzzh.monitor.dao.ts.TsTopSqlWriter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;

/**
 * 采集执行器：取本分片实例 → 工厂解析采集器 → 采集 → 批量写时序（§14.1）。
 * <p>节点内并发：固定大小线程池并行采集分到的实例，线程池大小即为访问目标库的最大并发。
 */
@Component
public class CollectRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectRunner.class);

    @Resource
    private CollectorFactory collectorFactory;
    @Resource
    private TsMetricWriter tsMetricWriter;
    @Resource
    private TsTextWriter tsTextWriter;
    @Resource
    private TsTopSqlWriter tsTopSqlWriter;
    @Resource
    private TsPgQueryStatWriter tsPgQueryStatWriter;
    @Resource
    private TsCapacityObjectWriter tsCapacityObjectWriter;
    @Resource
    private TsLongConnWriter tsLongConnWriter;
    @Resource
    private TsSlowSqlSampleWriter tsSlowSqlSampleWriter;
    @Resource
    private CollectLogMapper collectLogMapper;
    @Resource
    private DbInstanceMapper dbInstanceMapper;
    @Resource
    private ConnectionFailureAlertService connectionFailureAlertService;
    @Resource
    private TsParamQueryDao paramQueryDao;
    @Resource
    private CollectProperties props;

    private ExecutorService pool;
    private int poolSize;

    /** 节点内连续失败计数（分钟级连接失败次数）：达阈值后将实例标为 abnormal。 */
    private final ConcurrentHashMap<Long, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    /** 连续失败多少次后标记实例 status=abnormal（同时是内置连接失败告警的触发阈值）。 */
    private static final int FAILURE_THRESHOLD = ConnectionFailureAlertService.FAILURE_THRESHOLD;

    /**
     * 同频率 Job 重入保护：整轮超时预算（instanceTimeoutMs × 批次数）可能大于调度周期
     * （如慢目标库拖长分钟级采集），上一轮未结束时直接丢弃本轮，避免任务在无界队列中堆积。
     * xxl-job 侧仍建议配置"丢弃后续调度"阻塞策略，此处是代码级兜底。
     */
    private final ConcurrentHashMap<CollectFrequency, ReentrantLock> roundLocks = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        this.poolSize = Math.max(1, props.getPoolSize());
        // 无界队列 + 固定线程数：队列永不满，拒绝策略仅在池已 shutdown 后提交时触发，
        // 用 AbortPolicy 让这种编程错误显式抛出而非静默在调用线程执行
        this.pool = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), namedThreadFactory("collect-worker-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void run(List<CollectTargetVo> instances, CollectFrequency frequency) {
        if (instances == null || instances.isEmpty()) {
            return;
        }
        ReentrantLock roundLock = roundLocks.computeIfAbsent(frequency, k -> new ReentrantLock());
        if (!roundLock.tryLock()) {
            log.warn("上一轮[{}]采集尚未结束，丢弃本轮调度（实例 {} 个），请关注目标库响应或调大采集周期",
                    frequency, instances.size());
            return;
        }
        try {
            doRun(instances, frequency);
        } finally {
            roundLock.unlock();
        }
    }

    private void doRun(List<CollectTargetVo> instances, CollectFrequency frequency) {
        // 显式过滤暂停实例：paused 一律跳过并打日志，便于排查"实例为何没数据"
        List<CollectTargetVo> active = new ArrayList<>(instances.size());
        int skipped = 0;
        for (CollectTargetVo ins : instances) {
            if (InstanceStatus.PAUSED.equalsIgnoreCase(ins.getStatus())) {
                skipped++;
                log.info("跳过暂停采样实例 id={} {}:{} status={}",
                        ins.getId(), ins.getHost(), ins.getPort(), ins.getStatus());
                continue;
            }
            active.add(ins);
        }
        if (skipped > 0) {
            log.info("本轮[{}]跳过暂停采样实例 {} 个，实际采集 {} 个", frequency, skipped, active.size());
        }
        if (active.isEmpty()) {
            return;
        }
        // invokeAll 带整轮超时：总预算 = 单实例超时 × 批次数（按线程池并发折算），
        // 到期后未完成的任务被统一取消，避免个别实例拖垮整轮采集。
        // started 标记用于区分"从未执行就被取消"（需补写采集日志）与"执行中被打断"
        // （任务自身 finally 会写日志与可用性，JDBC 查询受 socketTimeout 约束、耗时有界）。
        List<Callable<Void>> tasks = new ArrayList<>(active.size());
        List<AtomicBoolean> startedFlags = new ArrayList<>(active.size());
        // 在途任务计数：invokeAll 超时取消后，执行中的任务不会立即停止（JDBC 不响应 interrupt），
        // doRun 必须等它们全部收尾再返回，否则 roundLock 提前释放，下一轮会与在途任务重叠
        AtomicInteger inFlight = new AtomicInteger();
        for (CollectTargetVo ins : active) {
            var started = new AtomicBoolean(false);
            startedFlags.add(started);
            tasks.add(() -> {
                // 任务入口清理中断标记：固定线程池复用 worker，上一轮 cancel(true) 遗留的中断位
                // 会被下一个任务继承，导致真实连接失败被误判为"整轮取消"而跳过可用性判定
                Thread.interrupted();
                started.set(true);
                inFlight.incrementAndGet();
                try {
                    collectOne(ins, frequency);
                } finally {
                    inFlight.decrementAndGet();
                    // 任务出口再次清理，避免污染同 worker 的下一个任务
                    Thread.interrupted();
                }
                return null;
            });
        }
        int batches = (active.size() + poolSize - 1) / poolSize;
        // 超时预算按频率区分：小时/天级含重查询（information_schema.tables、表 I/O 差值等），
        // 远程实例单轮 20~50s 属正常，不能沿用分钟级 15s 预算，否则任务被整轮取消、
        // 中断标记导致监控库落库失败（CannotGetJdbcConnectionException）
        long totalTimeoutMs = props.timeoutMsFor(frequency) * batches;
        try {
            List<Future<Void>> futures = pool.invokeAll(tasks, totalTimeoutMs, TimeUnit.MILLISECONDS);
            for (int i = 0; i < futures.size(); i++) {
                Future<Void> f = futures.get(i);
                CollectTargetVo ins = active.get(i);
                if (f.isCancelled()) {
                    if (startedFlags.get(i).get()) {
                        // 执行中被打断：任务线程仍会跑完 collectOne 的 finally（写采集日志；
                        // 可用性在"整轮取消且未确认连上"时跳过，避免慢实例被误判），此处仅记录
                        log.warn("实例 {} 采集执行中被取消（整轮预算 {}ms 已耗尽），等待任务自行收尾", ins.getId(), totalTimeoutMs);
                    } else {
                        // 从未执行即被取消：任务不会产生任何日志，补写一条失败采集日志便于排查；
                        // 未探测过连接，不写 availability，避免误判实例异常
                        log.warn("实例 {} 采集任务未执行即被取消（整轮预算 {}ms 已耗尽）", ins.getId(), totalTimeoutMs);
                        writeCollectLog(ins.getId(), frequency, System.currentTimeMillis(), false,
                                0, 0, 0, "整轮采集超时，任务未执行即被取消");
                    }
                    continue;
                }
                try {
                    f.get();
                } catch (ExecutionException e) {
                    log.error("实例 {} 采集任务异常", ins.getId(), e.getCause());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 等待"执行中被取消"的在途任务全部收尾后才返回（进而释放 roundLock），
            // 中断路径也不例外，否则下一轮会与在途任务重叠、差值基线错乱
            awaitInFlight(inFlight, frequency, totalTimeoutMs);
        }
    }

    /**
     * 等待在途采集任务收尾。JDBC 语句受 queryTimeout/socketTimeout 约束，正常情况下等待有界；
     * 但 collectOne 还包含监控库写入等无超时环节，为防监控库自身阻塞导致 roundLock 被永久占住，
     * 设置绝对上界（整轮预算 + 60s 收尾余量），超限后打 error 并放弃等待——此时僵尸任务与
     * 下一轮可能短暂重叠，但由 TargetConnectionCache 的实例锁保证连接互斥，属可接受的降级。
     */
    private void awaitInFlight(AtomicInteger inFlight, CollectFrequency frequency, long totalTimeoutMs) {
        if (inFlight.get() == 0) {
            return;
        }
        long boundMs = totalTimeoutMs + 60_000L;
        long waitStart = System.currentTimeMillis();
        boolean interrupted = false;
        try {
            while (inFlight.get() > 0) {
                if (System.currentTimeMillis() - waitStart > boundMs) {
                    log.error("本轮[{}]等待在途采集任务收尾超过上界 {}ms，仍有 {} 个未结束，放弃等待并释放轮锁，"
                            + "请排查监控库写入是否阻塞", frequency, boundMs, inFlight.get());
                    return;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    // 记录中断但继续等待收尾（等待本身有绝对上界，不会无限阻塞）
                    interrupted = true;
                }
            }
            long waited = System.currentTimeMillis() - waitStart;
            if (waited > 1000) {
                log.warn("本轮[{}]等待被取消的在途采集任务收尾 {}ms", frequency, waited);
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** 单实例采集：并发度由固定线程池控制。 */
    private void collectOne(CollectTargetVo ins, CollectFrequency frequency) {
        collectOne(ins, frequency, null);
    }

    /**
     * 单实例按需采集（不走整轮轮锁），用于配置快照补采等场景。
     */
    public boolean runSingle(CollectTargetVo ins, CollectFrequency frequency, List<String> collectItems) {
        if (ins == null || InstanceStatus.PAUSED.equals(ins.getStatus())) {
            return false;
        }
        return collectOne(ins, frequency, collectItems);
    }

    /** 单实例采集：并发度由固定线程池控制。 */
    private boolean collectOne(CollectTargetVo ins, CollectFrequency frequency, List<String> collectItems) {
        long startMs = System.currentTimeMillis();
        boolean success = false;
        boolean connectionOk = false;
        String errorMessage = null;
        int metricCount = 0, textCount = 0, objectCount = 0;
        DatabaseCollector collector = null;
        try {
            collector = collectorFactory.getCollector(ins.getDbType(), ins.getDbVersion());
            CollectResult result = collector.collect(buildRequest(ins, frequency, collectItems));
            if (result.isSuccess()) {
                connectionOk = true;
                metricCount = result.getPoints() == null ? 0 : result.getPoints().size();
                textCount   = result.getTextPoints() == null ? 0 : result.getTextPoints().size();
                objectCount = result.getObjectPoints() == null ? 0 : result.getObjectPoints().size();
                // 整轮预算耗尽的 cancel(true) 可能在采集期间已置中断标记（JDBC 不响应中断，采集
                // 正常返回）；Druid 取连接对中断敏感，带标记落库必失败（CannotGetJdbcConnectionException）。
                // 数据已在手，清除标记完成落库——任务随即自然结束，不影响整轮取消语义
                if (Thread.interrupted()) {
                    log.warn("实例 {} 采集完成时已被整轮超时取消，清除中断标记后继续落库（建议调大 {} 级超时预算）",
                            ins.getId(), toFreqCode(frequency));
                }
                writeResult(ins.getId(), frequency, result);
                if (result.hasItemErrors()) {
                    // 部分采集项失败：数据已写入但标记为失败，错误信息列出失败项
                    success = false;
                    errorMessage = String.join("; ", result.getItemErrors());
                    log.warn("实例 {} 采集部分失败: {}", ins.getId(), errorMessage);
                } else {
                    success = true;
                    log.debug("实例 {} 采集成功，数值点 {} 文本点 {} 对象点 {} TopSQL {}",
                            ins.getId(), metricCount, textCount, objectCount, result.getTopSqlPoints().size());
                    if (frequency == CollectFrequency.MINUTE) {
                        bootstrapConfigIfNeeded(ins, collector);
                    }
                }
            } else {
                // 连接级别失败：AvailabilityItem 未运行，需补写 availability=0
                errorMessage = result.getError();
                log.warn("实例 {} 采集失败: {}", ins.getId(), errorMessage);
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("实例 {} 采集异常", ins.getId(), e);
        } finally {
            // 仅 1m 频率做可用性探测与实例状态联动（避免 1h/1d 偶发超时误判）。
            // 整轮预算耗尽被取消（cancel(true) 置中断标记）且未连上时，实例可能只是"慢"而非"宕"，
            // 跳过 availability=0 与失败计数，避免慢实例被误标 abnormal；连接成功则正常走恢复逻辑。
            // 先读取再清除中断标记：cancelledByRound 判定需要标记，而后续可用性/采集日志
            // 都要写监控库，带中断标记取 Druid 连接会失败
            boolean cancelledByRound = Thread.interrupted();
            if (frequency == CollectFrequency.MINUTE) {
                if (connectionOk || !cancelledByRound) {
                    handleAvailability(ins, collector, connectionOk, errorMessage);
                } else {
                    log.warn("实例 {} 采集被整轮超时取消且未确认连接失败，跳过可用性判定", ins.getId());
                }
            }
            writeCollectLog(ins.getId(), frequency, startMs, success,
                    metricCount, textCount, objectCount, errorMessage);
        }
        return success;
    }

    /**
     * 分钟级采集成功后，若天级配置表尚无新鲜数据则补跑配置采集项（各库 SPI 声明）。
     */
    private void bootstrapConfigIfNeeded(CollectTargetVo ins, DatabaseCollector collector) {
        String marker = collector.configSnapshotMarkerMetric();
        List<String> items = collector.configSnapshotItemCodes();
        if (!StringUtils.hasText(marker) || items == null || items.isEmpty()) {
            return;
        }
        Map<String, Double> values = paramQueryDao.latestNumericParams(ins.getId(), List.of(marker));
        if (values.containsKey(marker)) {
            return;
        }
        log.info("实例 {} 配置参数尚无新鲜快照，补采 {}", ins.getId(), items);
        if (!runSingle(ins, CollectFrequency.DAILY, items)) {
            log.warn("实例 {} 配置快照补采失败", ins.getId());
        }
    }

    /**
     * 可用性探测与实例状态联动（P2-4）。
     * <ul>
     *   <li>连接失败：写对应类型的 {@code availability=0} 指标，累计连续失败 {@value FAILURE_THRESHOLD} 次后
     *       将实例 status 更新为 {@code abnormal}，并联动系统内置连接失败告警（一级、全渠道通知）。</li>
     *   <li>连接成功：清零失败计数；若实例之前为 {@code abnormal} 则恢复为 {@code normal}，
     *       并联动告警事件恢复与恢复通知。</li>
     * </ul>
     */
    private void handleAvailability(CollectTargetVo ins, DatabaseCollector collector, boolean connectionOk, String errorMessage) {
        long instanceId = ins.getId();
        long ts = System.currentTimeMillis();
        if (connectionOk) {
            consecutiveFailures.remove(instanceId);
            // 连接恢复：无条件尝试条件恢复，不依赖轮初 Vo 快照或本地失败计数
            // （进程重启后计数丢失、Vo 又滞后时会漏恢复）。UPDATE 自带 status='abnormal'
            // 守卫，已是 normal 时为空操作，也不会覆盖 paused 等人工状态
            restoreNormalIfAbnormal(ins);
        } else {
            if (collector == null) {
                log.warn("实例 {} 未找到采集器，跳过 availability 指标写入", instanceId);
                return;
            }
            // 写 availability=0 指标点（由采集器声明具体编码）
            writeNumeric(instanceId, CollectFrequency.MINUTE,
                    List.of(new MetricPoint(collector.availabilityMetricCode(), 0.0, ts)));
            int fails = consecutiveFailures
                    .computeIfAbsent(instanceId, k -> new AtomicInteger(0))
                    .incrementAndGet();
            if (fails >= FAILURE_THRESHOLD) {
                markAbnormal(instanceId, fails);
                // 系统内置连接失败告警：一级、全渠道通知；服务内部按活跃事件归并去重，
                // 持续宕机期间每轮进入也不会重复建单/通知
                connectionFailureAlertService.onInstanceDown(
                        instanceId, ins.getInstanceName(), fails, errorMessage);
            }
        }
    }

    /** 连接恢复时把 abnormal 实例还原为 normal（带 status 守卫，不覆盖 paused 等其他状态），并联动告警恢复。 */
    private void restoreNormalIfAbnormal(CollectTargetVo ins) {
        Long instanceId = ins.getId();
        try {
            int updated = dbInstanceMapper.update(
                    null,
                    new LambdaUpdateWrapper<DbInstance>()
                            .eq(DbInstance::getId, instanceId)
                            .eq(DbInstance::getStatus, InstanceStatus.ABNORMAL)
                            .set(DbInstance::getStatus, InstanceStatus.NORMAL));
            if (updated > 0) {
                log.info("实例 {} 连接已恢复，status → normal", instanceId);
                // 事件流转 recovered + 发送恢复通知（带状态守卫，人工终态不改写）
                connectionFailureAlertService.onInstanceRecovered(instanceId, ins.getInstanceName());
            }
        } catch (Exception e) {
            log.warn("恢复实例 {} status 失败", instanceId, e);
        }
    }

    /** 连续失败达阈值时标记 abnormal（带 status 守卫，与恢复侧对称，不覆盖 paused 等人工状态）。 */
    private void markAbnormal(Long instanceId, int fails) {
        try {
            int updated = dbInstanceMapper.update(
                    null,
                    new LambdaUpdateWrapper<DbInstance>()
                            .eq(DbInstance::getId, instanceId)
                            .notIn(DbInstance::getStatus, InstanceStatus.ABNORMAL, InstanceStatus.PAUSED)
                            .set(DbInstance::getStatus, InstanceStatus.ABNORMAL));
            if (updated > 0) {
                log.warn("实例 {} 连续 {} 次连接失败，status → abnormal", instanceId, fails);
            }
        } catch (Exception e) {
            log.warn("标记实例 {} abnormal 失败", instanceId, e);
        }
    }

    /** 同步写采集日志；写失败仅告警，不影响采集主流程。 */
    private void writeCollectLog(Long instanceId, CollectFrequency frequency, long startMs,
                                 boolean success, int metricCount, int textCount, int objectCount,
                                 String errorMessage) {
        try {
            CollectLog entry = new CollectLog();
            entry.setInstanceId(instanceId);
            entry.setFrequency(toFreqCode(frequency));
            entry.setCollectTime(OffsetDateTime.now());
            entry.setDurationMs((int) (System.currentTimeMillis() - startMs));
            entry.setSuccess(success);
            entry.setMetricCount(metricCount);
            entry.setTextCount(textCount);
            entry.setObjectCount(objectCount);
            if (errorMessage != null && errorMessage.length() > 2000) {
                errorMessage = errorMessage.substring(0, 2000);
            }
            entry.setErrorMessage(errorMessage);
            collectLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("写采集日志失败 instance={}", instanceId, e);
        }
    }

    private static String toFreqCode(CollectFrequency frequency) {
        if (frequency == null) return "1m";
        return switch (frequency) {
            case MINUTE -> "1m";
            case HOURLY -> "1h";
            case DAILY  -> "1d";
        };
    }

    /** 落库采集结果的全部类型点：数值（按频率路由）、文本（覆盖变更）、对象级容量、Top SQL、长连接明细。 */
    private void writeResult(Long instanceId, CollectFrequency frequency, CollectResult result) {
        writeNumeric(instanceId, frequency, result.getPoints());
        writeText(instanceId, frequency, result.getTextPoints());
        writeObjects(instanceId, result.getObjectPoints());
        writeTopSql(instanceId, result.getTopSqlPoints());
        writePgQueryStats(instanceId, result.getPgQueryStatPoints());
        writeLongConns(instanceId, result.getLongConnPoints());
        writeSlowSqlSamples(instanceId, result.getSlowSqlSamplePoints());
    }

    private void writeSlowSqlSamples(Long instanceId, List<SlowSqlSamplePoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<TsSlowSqlSampleWriter.TsSlowSqlSamplePoint> rows = points.stream()
                .map(p -> new TsSlowSqlSampleWriter.TsSlowSqlSamplePoint(
                        p.threadId(), p.eventId(), p.connUser(), p.connHost(),
                        p.schemaName(), p.digest(), p.sqlText(),
                        p.execTimeUs(), p.lockTimeUs(), p.rowsExamined(), p.rowsSent(),
                        p.sortRows(), p.noIndexUsed(), p.tmpTables(), p.tmpDiskTables(),
                        p.timestampMillis()))
                .toList();
        tsSlowSqlSampleWriter.batchWrite(instanceId, rows);
    }

    private void writeNumeric(Long instanceId, CollectFrequency frequency, List<MetricPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<TsMetricWriter.TsMetricPoint> rows = points.stream()
                .map(p -> new TsMetricWriter.TsMetricPoint(
                        instanceId, p.metric(), p.value(), p.timestampMillis()))
                .toList();
        tsMetricWriter.batchWrite(frequency, rows);
    }

    private void writeText(Long instanceId, CollectFrequency frequency, List<TextMetricPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<TsTextWriter.TsTextPoint> rows = points.stream()
                .map(p -> new TsTextWriter.TsTextPoint(
                        instanceId, p.metric(), p.valueText(), p.valueHash(), p.timestampMillis()))
                .toList();
        tsTextWriter.batchWrite(frequency, rows);
    }

    private void writeObjects(Long instanceId, List<ObjectMetricPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<TsCapacityObjectWriter.TsCapacityObjectPoint> rows = points.stream()
                .map(p -> new TsCapacityObjectWriter.TsCapacityObjectPoint(
                        instanceId, p.metric(), p.objectType(), p.objectName(), p.value(), p.timestampMillis()))
                .toList();
        tsCapacityObjectWriter.batchWrite(rows);
    }

    private void writeLongConns(Long instanceId, List<LongConnPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<TsLongConnWriter.TsLongConnPoint> rows = points.stream()
                .map(p -> new TsLongConnWriter.TsLongConnPoint(
                        p.connId(), p.connUser(), p.connHost(), p.connDb(),
                        p.command(), p.timeSeconds(), p.state(), p.info(),
                        p.timestampMillis()))
                .toList();
        tsLongConnWriter.batchWrite(instanceId, rows);
    }

    private void writePgQueryStats(Long instanceId, List<PgQueryStatPoint> points) {
        if (points == null || points.isEmpty()) return;
        List<TsPgQueryStatWriter.TsPgQueryStatPoint> rows = points.stream()
                .map(p -> new TsPgQueryStatWriter.TsPgQueryStatPoint(
                        p.databaseName(), p.userName(), p.queryId(), p.queryText(),
                        p.deltaCalls(), p.deltaExecTimeMs(), p.deltaRows(),
                        cn.hutool.json.JSONUtil.toJsonStr(p.metrics()),
                        p.statsReset(), p.deallocations(), p.timestampMillis()))
                .toList();
        tsPgQueryStatWriter.batchWrite(instanceId, rows);
    }
    private void writeTopSql(Long instanceId, List<TopSqlPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<TsTopSqlWriter.TsTopSqlPoint> rows = points.stream()
                .map(p -> new TsTopSqlWriter.TsTopSqlPoint(
                        instanceId, p.schemaName(), p.digest(), p.digestText(),
                        p.countStar(), p.sumTimerWait(), p.rowsExamined(), p.rowsSent(),
                        p.deltaCount(), p.deltaTimerWait(), p.avgTimerWaitUs(),
                        p.deltaRowsExamined(), p.deltaRowsSent(),
                        p.deltaLockTime(), p.deltaSortRows(), p.deltaNoIndexUsed(),
                        p.deltaTmpTables(), p.deltaTmpDiskTables(),
                        p.timestampMillis()))
                .toList();
        tsTopSqlWriter.batchWrite(rows);
    }

    private CollectRequest buildRequest(CollectTargetVo ins, CollectFrequency frequency,
                                        List<String> collectItems) {
        CollectRequest request = new CollectRequest();
        request.setInstanceId(ins.getId());
        request.setDbType(DbType.of(ins.getDbType()));
        request.setVersion(ins.getDbVersion());
        request.setFrequency(frequency);
        request.setCollectItems(collectItems);
        request.setConnSourceWhitelist(ins.getConnSourceWhitelist());
        request.setTarget(TargetDataSourceFactory.from(ins));
        return request;
    }
    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefix + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
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
