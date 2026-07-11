package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Windows 主机运行时长：now - windows_system_boot_time_timestamp（epoch 秒）。
 * 产出：host.uptime（秒）。
 */
@Component
public class WindowsUptimeItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.uptime.windows";
    }

    @Override
    public Set<String> families() {
        return Set.of("windows_system_boot_time_timestamp");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        Double boot = snapshot.firstValue("windows_system_boot_time_timestamp");
        if (boot == null || boot <= 0) {
            return;
        }
        long ts = snapshot.timestampMillis();
        double uptime = ts / 1000.0 - boot;
        if (uptime > 0) {
            sink.addNumeric("host.uptime", Math.round(uptime), ts);
        }
    }
}
