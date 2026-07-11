package com.lzzh.monitor.collector.host.prom;

import java.util.List;
import java.util.Map;

/**
 * 一次 exporter 拉取解析出的指标快照：metric family → 采样列表。
 * 仅包含白名单内的 metric family（{@link PromTextParser} 按需解析）。
 */
public record PromSnapshot(Map<String, List<PromSample>> families, long timestampMillis) {

    /** 取指定 family 的全部采样，不存在时返回空列表。 */
    public List<PromSample> samples(String family) {
        return families.getOrDefault(family, List.of());
    }

    /** 取无标签（或任意首个）采样的值，不存在时返回 null。 */
    public Double firstValue(String family) {
        List<PromSample> list = samples(family);
        return list.isEmpty() ? null : list.get(0).value();
    }
}
