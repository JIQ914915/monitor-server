package com.lzzh.monitor.collector.spi.model;

/** 标准化指标点（采集结果的最小单元）。 */
public record MetricPoint(String metric, double value, long timestampMillis) {
}
