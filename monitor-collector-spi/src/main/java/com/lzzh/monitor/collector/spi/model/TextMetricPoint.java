package com.lzzh.monitor.collector.spi.model;

/**
 * 文本/状态指标点（§9.1 文本/状态加工，覆盖变更存储）。
 * <p>复制状态、复制错误信息、GTID、参数文本、SQL 文本等非数值指标，
 * 采用"变更检测"加工：仅在值发生变化（valueHash 不同）时落 metric_text_data。
 * valueHash 由采集侧基于 valueText 计算（如 SHA-256），写入层据此去重。
 *
 * @param metric        指标编码，如 mysql.replication.last_error
 * @param valueText     文本/状态值（敏感内容需脱敏后传入）
 * @param valueHash     值哈希，用于变更检测与去重
 * @param timestampMillis 采集时间（毫秒）
 */
public record TextMetricPoint(String metric, String valueText, String valueHash, long timestampMillis) {
}
