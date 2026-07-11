package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSample;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Windows 磁盘 IO：windows_logical_disk_*（windows_exporter，counter 差值换算速率）。
 * 产出：host.diskio.util_max（1 - idle 时间占比）/ host.diskio.read_bytes / host.diskio.write_bytes
 * 以及按盘符的明细文本 host.diskio.detail（繁忙度 + 读写速率，按繁忙度倒序）。
 */
@Component
public class WindowsDiskIoItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.diskio.windows";
    }

    @Override
    public Set<String> families() {
        return Set.of("windows_logical_disk_idle_seconds_total",
                "windows_logical_disk_read_bytes_total", "windows_logical_disk_write_bytes_total");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        long ts = snapshot.timestampMillis();

        // 盘符 → 明细行；Windows 用 idle 时间占比反推繁忙度
        Map<String, DiskIoItem.DiskIoDetailRow> rows = new TreeMap<>();
        double totalRead = 0, totalWrite = 0;
        boolean hasRead = false, hasWrite = false;

        for (PromSample s : snapshot.samples("windows_logical_disk_idle_seconds_total")) {
            String volume = s.label("volume");
            if (WindowsDiskItem.isExcludedVolume(volume)) {
                continue;
            }
            Double idleRate = deltaCache.rate(hostId, "diskio.util." + volume, s.value(), ts);
            if (idleRate != null) {
                rows.computeIfAbsent(volume, DiskIoItem.DiskIoDetailRow::new)
                        .utilPercent = CpuItem.round2(CpuItem.clampPercent((1.0 - idleRate) * 100.0));
            }
        }
        for (PromSample s : snapshot.samples("windows_logical_disk_read_bytes_total")) {
            String volume = s.label("volume");
            if (WindowsDiskItem.isExcludedVolume(volume)) {
                continue;
            }
            totalRead += s.value();
            hasRead = true;
            Double rate = deltaCache.rate(hostId, "diskio.read." + volume, s.value(), ts);
            if (rate != null) {
                rows.computeIfAbsent(volume, DiskIoItem.DiskIoDetailRow::new).readBytes = Math.round(rate);
            }
        }
        for (PromSample s : snapshot.samples("windows_logical_disk_write_bytes_total")) {
            String volume = s.label("volume");
            if (WindowsDiskItem.isExcludedVolume(volume)) {
                continue;
            }
            totalWrite += s.value();
            hasWrite = true;
            Double rate = deltaCache.rate(hostId, "diskio.write." + volume, s.value(), ts);
            if (rate != null) {
                rows.computeIfAbsent(volume, DiskIoItem.DiskIoDetailRow::new).writeBytes = Math.round(rate);
            }
        }

        double utilMax = rows.values().stream()
                .map(r -> r.utilPercent)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max().orElse(-1);
        if (utilMax >= 0) {
            sink.addNumeric("host.diskio.util_max", utilMax, ts);
        }
        if (hasRead) {
            Double rate = deltaCache.rate(hostId, "diskio.read", totalRead, ts);
            if (rate != null) {
                sink.addNumeric("host.diskio.read_bytes", Math.round(rate), ts);
            }
        }
        if (hasWrite) {
            Double rate = deltaCache.rate(hostId, "diskio.write", totalWrite, ts);
            if (rate != null) {
                sink.addNumeric("host.diskio.write_bytes", Math.round(rate), ts);
            }
        }
        DiskIoItem.emitDetail(rows.values(), sink, code(), ts);
    }
}
