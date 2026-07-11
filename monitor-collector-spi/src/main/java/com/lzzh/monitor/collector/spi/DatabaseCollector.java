package com.lzzh.monitor.collector.spi;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.CollectResult;
import com.lzzh.monitor.common.enums.DbType;

import java.util.List;

/** 采集器：一种数据库类型一个实现。 */
public interface DatabaseCollector {

    /** 支持的数据库类型。 */
    DbType supportedType();

    /** 是否支持该版本（由实现内部按 VersionResolver 判断）。 */
    boolean supportsVersion(String version);

    /** 连接失败时写入的可用性指标编码。 */
    String availabilityMetricCode();

    /** 执行采集，返回标准化指标点。 */
    CollectResult collect(CollectRequest request);

    /**
     * 配置快照采集项编码（天级落库，如 MySQL {@code variables}）。
     * <p>返回空表示该库类型暂不支持配置补采。
     */
    default List<String> configSnapshotItemCodes() {
        return List.of();
    }

    /**
     * 用于判断配置快照是否已存在的代表性指标（如 {@code mysql.var.max_connections}）。
     */
    default String configSnapshotMarkerMetric() {
        return null;
    }
}
