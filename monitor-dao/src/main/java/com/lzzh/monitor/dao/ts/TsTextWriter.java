package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.common.enums.CollectFrequency;

import java.util.List;

/**
 * 文本/状态指标写入（§9.1 覆盖变更存储）。
 * 仅在 value_hash 相对上次变化时写入，实现"变更检测"落库；
 * 按采集频率路由到 metric_text_data_1m / 1h / 1d（§21.2.5）。
 */
public interface TsTextWriter {

    /**
     * 批量写入文本指标点（内部按 value_hash 去重，未变化的点跳过）。
     *
     * @param frequency 采集频率，决定写入的频率分表
     * @param points    文本指标点
     */
    void batchWrite(CollectFrequency frequency, List<TsTextPoint> points);

    /** 文本指标点落库形态。 */
    record TsTextPoint(Long instanceId, String metric, String valueText, String valueHash, long timestampMillis) {
    }
}
