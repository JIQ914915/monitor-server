package com.lzzh.monitor.collector.postgresql;

import com.lzzh.monitor.collector.postgresql.item.PgMetricItem;
import com.lzzh.monitor.collector.postgresql.item.PgMetricSink;
import com.lzzh.monitor.collector.postgresql.item.PgSettingsItem;
import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.postgresql.version.PgVersionResolver;
import com.lzzh.monitor.collector.spi.AbstractDatabaseCollector;
import com.lzzh.monitor.collector.spi.TargetConnectionCache;
import com.lzzh.monitor.collector.spi.annotation.CollectorFor;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.CollectResult;
import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.common.enums.DbType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PostgreSQL 采集器（一期：基础监控闭环）：从缓存取只读目标连接 → 按采集项逐项采集 → 汇总标准化指标点。
 * <p>结构与 MySqlCollector 对齐：分钟级走常驻缓存连接（持实例锁），
 * 小时级/天级低频重查询走临时连接（不占锁）。
 */
@CollectorFor(dbType = DbType.POSTGRESQL, versions = {"14", "15", "16", "17", "18"})
public class PostgreSqlCollector extends AbstractDatabaseCollector {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlCollector.class);

    @Resource
    private PgVersionResolver resolver;
    /** 编码 → 采集项实现（启动时由 Spring 注入全部 PgMetricItem）。 */
    private Map<String, PgMetricItem> items;
    @Resource
    private List<PgMetricItem> itemList;
    /** 目标库连接缓存（SPI 层，MySQL/PG 共用）：每实例复用一条长连接。 */
    @Resource
    private TargetConnectionCache connectionCache;

    @PostConstruct
    void init() {
        this.items = itemList.stream().collect(Collectors.toMap(PgMetricItem::code, Function.identity()));
    }

    @Override
    public boolean supportsVersion(String version) {
        return resolver.resolve(version) != null;
    }

    @Override
    public String availabilityMetricCode() {
        return "pg.availability";
    }

    @Override
    public List<String> configSnapshotItemCodes() {
        return List.of(PgSettingsItem.CODE);
    }

    @Override
    public String configSnapshotMarkerMetric() {
        return "pg.setting.max_connections";
    }

    @Override
    public CollectResult collect(CollectRequest request) {
        PgVersionAdapter adapter = resolver.resolve(request.getVersion());
        if (adapter == null) {
            return CollectResult.fail(request.getInstanceId(), "无可用 PostgreSQL 版本适配器: " + request.getVersion());
        }
        if (request.getVersion() != null && !request.getVersion().startsWith(adapter.version())) {
            log.warn("实例 {} PostgreSQL {} 使用 {} 兼容适配器", request.getInstanceId(), request.getVersion(), adapter.version());
        }
        List<PgMetricItem> targets = resolveItems(request);
        Long instanceId = request.getInstanceId();
        PgMetricSink sink = new PgMetricSink();

        CollectResult failure = request.getFrequency() == CollectFrequency.MINUTE
                ? collectWithCachedConnection(instanceId, request, adapter, targets, sink)
                : collectWithEphemeralConnection(instanceId, request, adapter, targets, sink);
        if (failure != null) {
            return failure;
        }

        CollectResult result = CollectResult.ok(instanceId, sink.numeric());
        result.setTextPoints(sink.text());
        result.setObjectPoints(sink.objects());
        result.setTopSqlPoints(sink.topSql());
        result.setPgQueryStatPoints(sink.pgQueryStats());
        result.setPgOperationalEventPoints(sink.pgOperationalEvents());
        result.setSlowSqlSamplePoints(sink.slowSqlSamples());
        sink.getItemErrors().forEach(err -> result.addItemError(err.code(), err.message()));
        return result;
    }

    /**
     * 分钟级采集：常驻缓存连接 + 实例级互斥锁。
     *
     * @return 连接失败时的失败结果；成功执行（含部分采集项失败）返回 {@code null}
     */
    private CollectResult collectWithCachedConnection(Long instanceId, CollectRequest request,
                                                      PgVersionAdapter adapter,
                                                      List<PgMetricItem> targets, PgMetricSink sink) {
        var lock = connectionCache.instanceLock(instanceId);
        lock.lock();
        try {
            Connection conn;
            try {
                conn = connectionCache.borrow(instanceId, request.getTarget());
            } catch (Exception e) {
                log.error("PostgreSQL 建连失败 instanceId={}", instanceId, e);
                connectionCache.evict(instanceId);
                return CollectResult.fail(instanceId, e.getMessage());
            }
            if (runItems(instanceId, conn, request, adapter, targets, sink)) {
                connectionCache.evict(instanceId);
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    /**
     * 小时级/天级采集：临时连接、用完即关，不占实例锁。
     *
     * @return 连接失败时的失败结果；成功执行（含部分采集项失败）返回 {@code null}
     */
    private CollectResult collectWithEphemeralConnection(Long instanceId, CollectRequest request,
                                                         PgVersionAdapter adapter,
                                                         List<PgMetricItem> targets, PgMetricSink sink) {
        try (Connection conn = connectionCache.openEphemeral(instanceId, request.getTarget())) {
            runItems(instanceId, conn, request, adapter, targets, sink);
        } catch (Exception e) {
            log.error("PostgreSQL 临时连接建连失败 instanceId={} freq={}", instanceId, request.getFrequency(), e);
            return CollectResult.fail(instanceId, e.getMessage());
        }
        return null;
    }

    /**
     * 逐项执行采集，采集项失败记入 sink 的 itemErrors；连接断开时跳过剩余项。
     *
     * @return 连接是否已损坏（调用方据此决定驱逐缓存或直接关闭）
     */
    private boolean runItems(Long instanceId, Connection conn, CollectRequest request,
                             PgVersionAdapter adapter, List<PgMetricItem> targets, PgMetricSink sink) {
        for (PgMetricItem item : targets) {
            try {
                item.collect(conn, request, adapter, sink);
            } catch (Exception e) {
                log.warn("实例 {} 采集项 {} 失败: {}", instanceId, item.code(), e.getMessage());
                sink.addItemError(item.code(), e.getMessage());
                try {
                    if (!conn.isValid(0)) {
                        log.warn("实例 {} 连接已失效，跳过剩余采集项", instanceId);
                        return true;
                    }
                } catch (Exception ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 解析本次要执行的采集项：按 request.frequency 过滤，使 1m/1h/1d 三档各采各的。 */
    private List<PgMetricItem> resolveItems(CollectRequest request) {
        CollectFrequency freq = request.getFrequency();
        List<String> itemCodes = request.getCollectItems();
        boolean filterByCode = itemCodes != null && !itemCodes.isEmpty();
        List<PgMetricItem> result = new ArrayList<>();
        for (PgMetricItem item : items.values()) {
            if (filterByCode && !itemCodes.contains(item.code())) {
                continue;
            }
            if (freq == null || item.frequencies().contains(freq)) {
                result.add(item);
            }
        }
        result.sort(Comparator.comparing(PgMetricItem::code));
        return result;
    }
}
