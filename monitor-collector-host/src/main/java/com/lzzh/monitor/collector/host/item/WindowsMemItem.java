package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Windows 内存：windows_memory_*（windows_exporter）。
 * 产出：host.mem.usage / host.mem.available_bytes。
 * Windows 无 Linux 语义的 Swap 指标（页面文件机制不同），host.swap.usage 不产出。
 */
@Component
public class WindowsMemItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.mem.windows";
    }

    @Override
    public Set<String> families() {
        return Set.of("windows_memory_physical_total_bytes", "windows_memory_available_bytes");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        Double total = snapshot.firstValue("windows_memory_physical_total_bytes");
        Double available = snapshot.firstValue("windows_memory_available_bytes");
        if (total == null || available == null || total <= 0) {
            return;
        }
        long ts = snapshot.timestampMillis();
        sink.addNumeric("host.mem.usage",
                CpuItem.round2(CpuItem.clampPercent((1.0 - available / total) * 100.0)), ts);
        sink.addNumeric("host.mem.available_bytes", available, ts);
    }
}
