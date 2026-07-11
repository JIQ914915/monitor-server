package com.lzzh.monitor.collector.host;

import com.lzzh.monitor.collector.spi.model.MetricPoint;
import com.lzzh.monitor.collector.spi.model.TextMetricPoint;

import java.util.List;

/**
 * 主机采集结果。
 *
 * @param success    exporter 是否可达且解析成功
 * @param error      连接级错误信息（success=false 时）
 * @param points     数值指标点（host.*，主机维度，扇出写入由 Runner 负责）
 * @param textPoints 文本指标点（挂载点明细等）
 * @param itemErrors 采集项级错误（部分失败）
 */
public record HostCollectResult(boolean success,
                                String error,
                                List<MetricPoint> points,
                                List<TextMetricPoint> textPoints,
                                List<String> itemErrors) {

    public static HostCollectResult ok(List<MetricPoint> points, List<TextMetricPoint> textPoints,
                                       List<String> itemErrors) {
        return new HostCollectResult(true, null, points, textPoints, itemErrors);
    }

    public static HostCollectResult fail(String error) {
        return new HostCollectResult(false, error, List.of(), List.of(), List.of());
    }

    public boolean hasItemErrors() {
        return itemErrors != null && !itemErrors.isEmpty();
    }
}
