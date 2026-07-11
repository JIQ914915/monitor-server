package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.common.enums.CollectFrequency;

import java.util.List;

/**
 * TimescaleDB 数值时序写入抽象（§21.2）。
 * <p>按采集频率路由到 metric_data_1m / 1h / 1d 三张 Hypertable——分表是为满足 §12.2
 * 分级差异化保留（30天/180天/2年），保留/压缩策略以每张 Hypertable 为粒度。
 */
public interface TsMetricWriter {

    /** 按频率批量写入到对应频率表。 */
    void batchWrite(CollectFrequency frequency, List<TsMetricPoint> points);

    /** 单条指标点（与采集 SPI 的 MetricPoint 对应的落库形态）。 */
    record TsMetricPoint(Long instanceId, String metric, double value, long timestampMillis) {
    }
}
