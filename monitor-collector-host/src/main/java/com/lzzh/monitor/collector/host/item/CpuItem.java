package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSample;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * CPU 使用率与 IOWait：node_cpu_seconds_total（全核汇总，两次采样差值换算占比）。
 * 产出：host.cpu.usage / host.cpu.iowait。
 */
@Component
public class CpuItem implements HostMetricItem {

    static final String CPU_FAMILY = "node_cpu_seconds_total";

    @Override
    public String code() {
        return "host.cpu";
    }

    @Override
    public Set<String> families() {
        return Set.of(CPU_FAMILY);
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        double total = 0, idle = 0, iowait = 0;
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
            } else if ("iowait".equals(mode)) {
                iowait += s.value();
            }
        }
        if (!hasData) {
            return;
        }
        long ts = snapshot.timestampMillis();
        DeltaCache.Delta dTotal = deltaCache.delta(hostId, "cpu.total", total, ts);
        DeltaCache.Delta dIdle = deltaCache.delta(hostId, "cpu.idle", idle, ts);
        DeltaCache.Delta dIowait = deltaCache.delta(hostId, "cpu.iowait", iowait, ts);
        if (dTotal == null || dIdle == null || dTotal.diff() <= 0) {
            return;
        }
        double usage = clampPercent((1.0 - dIdle.diff() / dTotal.diff()) * 100.0);
        sink.addNumeric("host.cpu.usage", round2(usage), ts);
        if (dIowait != null) {
            sink.addNumeric("host.cpu.iowait", round2(clampPercent(dIowait.diff() / dTotal.diff() * 100.0)), ts);
        }
    }

    static double clampPercent(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
