package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;

import java.util.Set;

/**
 * 主机采集项：从一次 exporter 快照中加工产出 host.* 指标点。
 * 实现类以 @Component 注册，由 {@code HostCollector} 注入聚合。
 */
public interface HostMetricItem {

    /** 采集项编码（错误定位用）。 */
    String code();

    /** 本采集项需要解析的 Prometheus metric family 白名单。 */
    Set<String> families();

    /**
     * 加工产出指标点。
     *
     * @param hostId     主机 ID（delta 基线维度）
     * @param snapshot   本次拉取解析出的指标快照
     * @param deltaCache counter 差值缓存
     * @param sink       结果收集器
     */
    void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink);
}
