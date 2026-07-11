package com.lzzh.monitor.collector.mysql;

import com.lzzh.monitor.collector.mysql.item.VariablesItem;
import com.lzzh.monitor.collector.mysql.item.MetricSink;
import com.lzzh.monitor.collector.mysql.item.MySqlMetricItem;
import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.mysql.version.MySqlVersionResolver;
import com.lzzh.monitor.collector.spi.AbstractDatabaseCollector;
import com.lzzh.monitor.collector.spi.TargetConnectionCache;
import com.lzzh.monitor.collector.spi.annotation.CollectorFor;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.CollectResult;
import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.common.enums.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/** MySQL 采集器：从缓存取只读目标连接 → 按采集项逐项采集 → 汇总标准化指标点。 */
@CollectorFor(dbType = DbType.MYSQL, versions = {"5.6", "5.7", "8.0"})
public class MySqlCollector extends AbstractDatabaseCollector {

    private static final Logger log = LoggerFactory.getLogger(MySqlCollector.class);

    private final MySqlVersionResolver resolver;
    /** 编码 → 采集项实现（启动时由 Spring 注入全部 MySqlMetricItem）。 */
    private final Map<String, MySqlMetricItem> items;
    /** 目标库连接缓存（P2-7）：每实例复用一条长连接，避免每轮重建 TCP+认证开销。 */
    private final TargetConnectionCache connectionCache;

    public MySqlCollector(MySqlVersionResolver resolver, List<MySqlMetricItem> itemList,
                          TargetConnectionCache connectionCache) {
        this.resolver = resolver;
        this.items = itemList.stream().collect(Collectors.toMap(MySqlMetricItem::code, Function.identity()));
        this.connectionCache = connectionCache;
    }

    @Override
    public boolean supportsVersion(String version) {
        return resolver.resolve(version) != null;
    }

    @Override
    public String availabilityMetricCode() {
        return "mysql.availability";
    }

    @Override
    public List<String> configSnapshotItemCodes() {
        return List.of(VariablesItem.CODE);
    }

    @Override
    public String configSnapshotMarkerMetric() {
        return "mysql.var.max_connections";
    }

    @Override
    public CollectResult collect(CollectRequest request) {
        MySqlVersionAdapter adapter = resolver.resolve(request.getVersion());
        if (adapter == null) {
            return CollectResult.fail(request.getInstanceId(), "无可用 MySQL 版本适配器: " + request.getVersion());
        }
        if (request.getVersion() != null && !request.getVersion().startsWith(adapter.version())) {
            log.warn("实例 {} MySQL {} 使用 {} 兼容适配器", request.getInstanceId(), request.getVersion(), adapter.version());
        }
        List<MySqlMetricItem> targets = resolveItems(request);
        Long instanceId = request.getInstanceId();
        MetricSink sink = new MetricSink();

        // 分钟级走常驻缓存连接（持实例锁）；小时级/天级低频重查询走临时连接（不占锁），
        // 避免 information_schema 容量扫描等长查询阻塞同实例的分钟级采集与告警评估
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
        result.setLongConnPoints(sink.longConns());
        result.setSlowSqlSamplePoints(sink.slowSqlSamples());
        sink.getItemErrors().forEach(err -> result.addItemError(err.code(), err.message()));
        return result;
    }

    /**
     * 分钟级采集：常驻缓存连接 + 实例级互斥锁（与告警自定义 SQL 评估、连接健康检查互斥）。
     *
     * @return 连接失败时的失败结果；成功执行（含部分采集项失败）返回 {@code null}
     */
    private CollectResult collectWithCachedConnection(Long instanceId, CollectRequest request,
                                                      MySqlVersionAdapter adapter,
                                                      List<MySqlMetricItem> targets, MetricSink sink) {
        var lock = connectionCache.instanceLock(instanceId);
        lock.lock();
        try {
            // 从连接缓存取连接（未命中或失活时自动新建）
            Connection conn;
            try {
                conn = connectionCache.borrow(instanceId, request.getTarget());
            } catch (Exception e) {
                log.error("MySQL 建连失败 instanceId={}", instanceId, e);
                // 确保缓存中不残留坏条目
                connectionCache.evict(instanceId);
                return CollectResult.fail(instanceId, e.getMessage());
            }

            // 连接损坏时驱逐缓存条目，下轮采集会重建；不关闭 conn，由缓存统一管理生命周期
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
     * 稳态下每实例仍只占目标库 1 条连接，本方法执行期间短暂升至 2 条。
     *
     * @return 连接失败时的失败结果；成功执行（含部分采集项失败）返回 {@code null}
     */
    private CollectResult collectWithEphemeralConnection(Long instanceId, CollectRequest request,
                                                         MySqlVersionAdapter adapter,
                                                         List<MySqlMetricItem> targets, MetricSink sink) {
        try (Connection conn = connectionCache.openEphemeral(instanceId, request.getTarget())) {
            runItems(instanceId, conn, request, adapter, targets, sink);
        } catch (Exception e) {
            log.error("MySQL 临时连接建连失败 instanceId={} freq={}", instanceId, request.getFrequency(), e);
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
                             MySqlVersionAdapter adapter, List<MySqlMetricItem> targets, MetricSink sink) {
        for (MySqlMetricItem item : targets) {
            try {
                item.collect(conn, request, adapter, sink);
            } catch (Exception e) {
                log.warn("实例 {} 采集项 {} 失败: {}", instanceId, item.code(), e.getMessage());
                sink.addItemError(item.code(), e.getMessage());
                // 连接已断开则不必再执行后续 item，避免全部报 "connection closed"
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

    /**
     * 解析本次要执行的采集项：按 request.frequency 过滤，使 1m/1h/1d 三档各采各的。
     * <p>采集控制粒度只到实例层（status=paused 在 CollectRunner 跳过），不做指标级细化。
     */
    private List<MySqlMetricItem> resolveItems(CollectRequest request) {
        CollectFrequency freq = request.getFrequency();
        List<String> itemCodes = request.getCollectItems();
        boolean filterByCode = itemCodes != null && !itemCodes.isEmpty();
        List<MySqlMetricItem> result = new ArrayList<>();
        for (MySqlMetricItem item : items.values()) {
            if (filterByCode && !itemCodes.contains(item.code())) {
                continue;
            }
            if (freq == null || item.frequencies().contains(freq)) {
                result.add(item);
            }
        }
        // 按 code 字母序排序：global_status 恰好自然排在 innodb_buffer_pool / throughput 之前，
        // 保证全量状态快照先于复用它的采集项写入（若未来新增依赖项且字母序不满足，需改为显式置顶）
        result.sort(Comparator.comparing(MySqlMetricItem::code));
        return result;
    }
}
