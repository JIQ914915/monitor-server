package com.lzzh.monitor.collector.sqlserver;

import com.lzzh.monitor.collector.spi.AbstractDatabaseCollector;
import com.lzzh.monitor.collector.spi.TargetConnectionCache;
import com.lzzh.monitor.collector.spi.annotation.CollectorFor;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.CollectResult;
import com.lzzh.monitor.collector.sqlserver.item.SqlServerMetricItem;
import com.lzzh.monitor.collector.sqlserver.item.SqlServerMetricSink;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionResolver;
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

/** SQL Server 采集器：按版本、频率和采集项隔离执行。 */
@CollectorFor(dbType = DbType.SQLSERVER, versions = {"2012", "2014", "2016", "2017", "2019", "2022", "2025"})
public class SqlServerCollector extends AbstractDatabaseCollector {
    private static final Logger log = LoggerFactory.getLogger(SqlServerCollector.class);

    @Resource private SqlServerVersionResolver resolver;
    @Resource private List<SqlServerMetricItem> itemList;
    @Resource private TargetConnectionCache connectionCache;
    private Map<String, SqlServerMetricItem> items;

    @PostConstruct
    void init() {
        items = itemList.stream().collect(Collectors.toMap(SqlServerMetricItem::code, Function.identity()));
    }

    @Override
    public boolean supportsVersion(String version) {
        return resolver.resolve(version) != null;
    }

    @Override
    public String availabilityMetricCode() {
        return "sqlserver.availability";
    }

    @Override
    public CollectResult collect(CollectRequest request) {
        SqlServerVersionAdapter adapter = resolver.resolve(request.getVersion());
        if (adapter == null) {
            return CollectResult.fail(request.getInstanceId(), "暂不支持 SQL Server 版本: " + request.getVersion());
        }
        SqlServerMetricSink sink = new SqlServerMetricSink();
        List<SqlServerMetricItem> targets = resolveItems(request);
        Long instanceId = request.getInstanceId();
        CollectResult failure = request.getFrequency() == CollectFrequency.MINUTE
                ? collectCached(instanceId, request, adapter, targets, sink)
                : collectEphemeral(instanceId, request, adapter, targets, sink);
        if (failure != null) return failure;

        CollectResult result = CollectResult.ok(instanceId, sink.numeric());
        result.setTextPoints(sink.text());
        result.setObjectPoints(sink.objects());
        result.setTopSqlPoints(sink.topSql());
        result.setSqlServerDiagnosticEventPoints(sink.diagnosticEvents());
        sink.errors().forEach(error -> result.addItemError(error.code(), error.message()));
        return result;
    }

    private CollectResult collectCached(Long instanceId, CollectRequest request,
                                        SqlServerVersionAdapter adapter,
                                        List<SqlServerMetricItem> targets, SqlServerMetricSink sink) {
        var lock = connectionCache.instanceLock(instanceId);
        lock.lock();
        try {
            Connection conn;
            try {
                conn = connectionCache.borrow(instanceId, request.getTarget());
            } catch (Exception e) {
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

    private CollectResult collectEphemeral(Long instanceId, CollectRequest request,
                                           SqlServerVersionAdapter adapter,
                                           List<SqlServerMetricItem> targets, SqlServerMetricSink sink) {
        try (Connection conn = connectionCache.openEphemeral(instanceId, request.getTarget())) {
            runItems(instanceId, conn, request, adapter, targets, sink);
            return null;
        } catch (Exception e) {
            return CollectResult.fail(instanceId, e.getMessage());
        }
    }

    private boolean runItems(Long instanceId, Connection conn, CollectRequest request,
                             SqlServerVersionAdapter adapter, List<SqlServerMetricItem> targets,
                             SqlServerMetricSink sink) {
        for (SqlServerMetricItem item : targets) {
            try {
                item.collect(conn, request, adapter, sink);
            } catch (Exception e) {
                log.warn("实例 {} SQL Server 采集项 {} 失败: {}", instanceId, item.code(), e.getMessage());
                sink.addItemError(item.code(), e.getMessage());
                try {
                    if (!conn.isValid(0)) return true;
                } catch (Exception ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<SqlServerMetricItem> resolveItems(CollectRequest request) {
        List<SqlServerMetricItem> result = new ArrayList<>();
        boolean filter = request.getCollectItems() != null && !request.getCollectItems().isEmpty();
        for (SqlServerMetricItem item : items.values()) {
            if (filter && !request.getCollectItems().contains(item.code())) continue;
            if (request.getFrequency() == null || item.frequencies().contains(request.getFrequency())) {
                result.add(item);
            }
        }
        result.sort(Comparator.comparing(SqlServerMetricItem::code));
        return result;
    }
}
