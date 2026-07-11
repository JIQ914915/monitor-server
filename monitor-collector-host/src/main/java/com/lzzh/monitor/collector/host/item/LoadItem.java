package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSample;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 系统负载：node_load1 / node_load5，单核负载 = load1 / CPU 核数
 * （核数按 node_cpu_seconds_total{mode="idle"} 的 cpu 标签去重统计）。
 * 产出：host.load.load1 / host.load.load5 / host.load.per_core。
 */
@Component
public class LoadItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.load";
    }

    @Override
    public Set<String> families() {
        return Set.of("node_load1", "node_load5", CpuItem.CPU_FAMILY);
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        long ts = snapshot.timestampMillis();
        Double load1 = snapshot.firstValue("node_load1");
        Double load5 = snapshot.firstValue("node_load5");
        if (load1 != null) {
            sink.addNumeric("host.load.load1", CpuItem.round2(load1), ts);
        }
        if (load5 != null) {
            sink.addNumeric("host.load.load5", CpuItem.round2(load5), ts);
        }
        if (load1 == null) {
            return;
        }
        Set<String> cores = new HashSet<>();
        for (PromSample s : snapshot.samples(CpuItem.CPU_FAMILY)) {
            if ("idle".equals(s.label("mode")) && s.label("cpu") != null) {
                cores.add(s.label("cpu"));
            }
        }
        if (!cores.isEmpty()) {
            sink.addNumeric("host.load.per_core", CpuItem.round2(load1 / cores.size()), ts);
        }
    }
}
