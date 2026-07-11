package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSample;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Windows CPU 使用率：windows_cpu_time_total{core,mode}（windows_exporter，counter 秒）。
 * 产出：host.cpu.usage。Windows 无 iowait 概念，host.cpu.iowait 不产出。
 * <p>与 Linux 侧 {@link CpuItem} 并存：快照中无对应指标族时静默跳过，按 exporter 类型自然分流。
 */
@Component
public class WindowsCpuItem implements HostMetricItem {

    static final String CPU_FAMILY = "windows_cpu_time_total";

    @Override
    public String code() {
        return "host.cpu.windows";
    }

    @Override
    public Set<String> families() {
        return Set.of(CPU_FAMILY);
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        double total = 0, idle = 0;
        boolean hasData = false;
        for (PromSample s : snapshot.samples(CPU_FAMILY)) {
            String mode = s.label("mode");
            if (mode == null) {
                continue;
            }
            hasData = true;
            total += s.value();
            if ("idle".equals(mode)) {
                idle += s.value();
            }
        }
        if (!hasData) {
            return;
        }
        long ts = snapshot.timestampMillis();
        // 与 Linux CpuItem 共用基线键：一台主机只会有一种 exporter，不会冲突
        DeltaCache.Delta dTotal = deltaCache.delta(hostId, "cpu.total", total, ts);
        DeltaCache.Delta dIdle = deltaCache.delta(hostId, "cpu.idle", idle, ts);
        if (dTotal == null || dIdle == null || dTotal.diff() <= 0) {
            return;
        }
        double usage = CpuItem.clampPercent((1.0 - dIdle.diff() / dTotal.diff()) * 100.0);
        sink.addNumeric("host.cpu.usage", CpuItem.round2(usage), ts);
    }
}
