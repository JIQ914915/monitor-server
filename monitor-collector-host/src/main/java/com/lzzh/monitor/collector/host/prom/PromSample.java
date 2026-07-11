package com.lzzh.monitor.collector.host.prom;

import java.util.Map;

/**
 * Prometheus 文本格式的单个采样：{@code name{labels} value}。
 *
 * @param labels 标签键值（无标签时为空 Map）
 * @param value  采样值
 */
public record PromSample(Map<String, String> labels, double value) {

    public String label(String key) {
        return labels.get(key);
    }
}
