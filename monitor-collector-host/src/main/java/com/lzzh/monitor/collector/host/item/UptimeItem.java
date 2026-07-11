package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 主机运行时长：node_time_seconds - node_boot_time_seconds。
 * 产出：host.uptime（秒）。
 */
@Component
public class UptimeItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.uptime";
    }

    @Override
    public Set<String> families() {
        return Set.of("node_time_seconds", "node_boot_time_seconds");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        Double now = snapshot.firstValue("node_time_seconds");
        Double boot = snapshot.firstValue("node_boot_time_seconds");
        if (now != null && boot != null && now > boot) {
            sink.addNumeric("host.uptime", Math.round(now - boot), snapshot.timestampMillis());
        }
    }
}
