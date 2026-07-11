package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 内存与 Swap：node_memory_* Gauge 直读。
 * 产出：host.mem.usage / host.mem.available_bytes / host.swap.usage。
 */
@Component
public class MemItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.mem";
    }

    @Override
    public Set<String> families() {
        return Set.of("node_memory_MemTotal_bytes", "node_memory_MemAvailable_bytes",
                "node_memory_SwapTotal_bytes", "node_memory_SwapFree_bytes");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        long ts = snapshot.timestampMillis();
        Double memTotal = snapshot.firstValue("node_memory_MemTotal_bytes");
        Double memAvailable = snapshot.firstValue("node_memory_MemAvailable_bytes");
        if (memTotal != null && memAvailable != null && memTotal > 0) {
            sink.addNumeric("host.mem.usage",
                    CpuItem.round2(CpuItem.clampPercent((1.0 - memAvailable / memTotal) * 100.0)), ts);
            sink.addNumeric("host.mem.available_bytes", memAvailable, ts);
        }
        Double swapTotal = snapshot.firstValue("node_memory_SwapTotal_bytes");
        Double swapFree = snapshot.firstValue("node_memory_SwapFree_bytes");
        if (swapTotal != null) {
            // 未配置 Swap（total=0）时记 0，规则侧不误报
            double usage = swapTotal <= 0 || swapFree == null
                    ? 0.0
                    : CpuItem.clampPercent((1.0 - swapFree / swapTotal) * 100.0);
            sink.addNumeric("host.swap.usage", CpuItem.round2(usage), ts);
        }
    }
}
