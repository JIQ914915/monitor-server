package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSample;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Windows 网络流量：windows_net_*（windows_exporter，counter 差值换算速率）。
 * 产出：host.net.recv_bytes / host.net.send_bytes。
 */
@Component
public class WindowsNetItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.net.windows";
    }

    @Override
    public Set<String> families() {
        return Set.of("windows_net_bytes_received_total", "windows_net_bytes_sent_total");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        long ts = snapshot.timestampMillis();
        Double recv = sumRate(hostId, snapshot, deltaCache, "windows_net_bytes_received_total", "net.recv", ts);
        if (recv != null) {
            sink.addNumeric("host.net.recv_bytes", Math.round(recv), ts);
        }
        Double send = sumRate(hostId, snapshot, deltaCache, "windows_net_bytes_sent_total", "net.send", ts);
        if (send != null) {
            sink.addNumeric("host.net.send_bytes", Math.round(send), ts);
        }
    }

    private static Double sumRate(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache,
                                  String family, String key, long ts) {
        double sum = 0;
        boolean hasData = false;
        for (PromSample s : snapshot.samples(family)) {
            String nic = s.label("nic");
            if (nic != null && nic.toLowerCase().contains("loopback")) {
                continue;
            }
            sum += s.value();
            hasData = true;
        }
        if (!hasData) {
            return null;
        }
        return deltaCache.rate(hostId, key, sum, ts);
    }
}
