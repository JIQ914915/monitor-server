package com.lzzh.monitor.collector.spi.model;

/**
 * 对象级数值指标点（§21.2.5 对象级/多维指标）。
 * <p>承载"分对象"的容量/资源明细：MySQL 表、Oracle 表空间/数据文件、
 * SQL Server 文件组、达梦对象等，落 metric_capacity_object 专用表。
 * 禁止把对象名编入 metric_code，改用 objectType + objectName 维度表达。
 *
 * @param metric          指标编码，如 capacity.data_size_bytes
 * @param objectType      对象类型，如 table / tablespace / datafile / filegroup
 * @param objectName      对象名，如 库.表名
 * @param value           指标值
 * @param timestampMillis 采集时间（毫秒）
 */
public record ObjectMetricPoint(String metric, String objectType, String objectName,
                                double value, long timestampMillis) {
}
