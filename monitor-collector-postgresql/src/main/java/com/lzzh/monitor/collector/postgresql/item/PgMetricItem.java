package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * PostgreSQL 采集项（可插拔）：一个采集项一个实现，复用 collector 建立好的只读连接。
 * 新增采集项 = 新增一个本接口的 @Component 实现，PostgreSqlCollector 自动纳入（§5.8）。
 *
 * <p>采集控制粒度只到<b>实例层</b>：status=paused 的实例在 CollectRunner 层整体跳过。
 * 采集超时统一由全局 CollectProperties.instanceTimeoutMs 控制。
 */
public interface PgMetricItem {

    /**
     * 常规轻量查询的默认语句超时（秒）：JDBC 层兜底，防止个别语句挂住线程拖垮整轮采集。
     * 重查询项（如容量统计）可自定义更长超时。
     */
    int DEFAULT_QUERY_TIMEOUT_SECONDS = 10;

    /** 采集项编码，如 connections、database_stat、replication。 */
    String code();

    /**
     * 执行采集（只读）。采集到的指标点投递到 sink。
     *
     * @param conn    目标库连接（由 collector 统一管理生命周期）
     * @param request 采集请求
     * @param adapter 命中的版本适配器（提供版本差异 SQL）
     * @param sink    指标点收集器
     */
    void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException;

    /**
     * 本采集项适用的采集频率档位。
     * <p>默认仅分钟级；小时级（容量）、天级（配置快照）采集项覆盖此方法。
     */
    default Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.MINUTE);
    }
}
