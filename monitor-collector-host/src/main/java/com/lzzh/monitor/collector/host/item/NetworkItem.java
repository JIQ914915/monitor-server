package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSample;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 网络流量：node_network_*（counter 差值换算速率，排除 lo 回环）。
 * 产出：host.net.recv_bytes / host.net.send_bytes。
 */
@Component
public class NetworkItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.net";
    }

    @Override
    public Set<String> families() {
        return Set.of("node_network_receive_bytes_total", "node_network_transmit_bytes_total");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        long ts = snapshot.timestampMillis();
        Double recv = sumRate(hostId, snapshot, deltaCache, "node_network_receive_bytes_total", "net.recv", ts);
        if (recv != null) {
            sink.addNumeric("host.net.recv_bytes", Math.round(recv), ts);
        }
        Double send = sumRate(hostId, snapshot, deltaCache, "node_network_transmit_bytes_total", "net.send", ts);
        if (send != null) {
            sink.addNumeric("host.net.send_bytes", Math.round(send), ts);
        }
    }

    private static Double sumRate(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache,
                                  String family, String key, long ts) {
        double sum = 0;
        boolean hasData = false;
        for (PromSample s : snapshot.samples(family)) {
            String device = s.label("device");
            if (device == null || "lo".equals(device)) {
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
