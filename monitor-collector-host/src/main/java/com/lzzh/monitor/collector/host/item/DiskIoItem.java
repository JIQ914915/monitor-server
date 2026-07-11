package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostJson;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSample;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 磁盘 IO：node_disk_*（counter 差值换算速率，排除 loop/ram/sr/fd 虚拟设备）。
 * 产出：host.diskio.util_max（最繁忙设备的 IO 时间占比）/ host.diskio.read_bytes / host.diskio.write_bytes
 * 以及按块设备的明细文本 host.diskio.detail（繁忙度 + 读写速率，按繁忙度倒序）。
 */
@Component
public class DiskIoItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.diskio";
    }

    @Override
    public Set<String> families() {
        return Set.of("node_disk_io_time_seconds_total",
                "node_disk_read_bytes_total", "node_disk_written_bytes_total");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        long ts = snapshot.timestampMillis();

        // 设备 → 各口径速率（util 为 IO 时间占比，read/write 为字节速率）
        Map<String, DiskIoDetailRow> rows = new TreeMap<>();
        double totalRead = 0, totalWrite = 0;
        boolean hasRead = false, hasWrite = false;

        for (PromSample s : snapshot.samples("node_disk_io_time_seconds_total")) {
            String device = s.label("device");
            if (isVirtualDevice(device)) {
                continue;
            }
            Double rate = deltaCache.rate(hostId, "diskio.util." + device, s.value(), ts);
            if (rate != null) {
                rows.computeIfAbsent(device, DiskIoDetailRow::new)
                        .utilPercent = CpuItem.round2(CpuItem.clampPercent(rate * 100.0));
            }
        }
        for (PromSample s : snapshot.samples("node_disk_read_bytes_total")) {
            String device = s.label("device");
            if (isVirtualDevice(device)) {
                continue;
            }
            totalRead += s.value();
            hasRead = true;
            Double rate = deltaCache.rate(hostId, "diskio.read." + device, s.value(), ts);
            if (rate != null) {
                rows.computeIfAbsent(device, DiskIoDetailRow::new).readBytes = Math.round(rate);
            }
        }
        for (PromSample s : snapshot.samples("node_disk_written_bytes_total")) {
            String device = s.label("device");
            if (isVirtualDevice(device)) {
                continue;
            }
            totalWrite += s.value();
            hasWrite = true;
            Double rate = deltaCache.rate(hostId, "diskio.write." + device, s.value(), ts);
            if (rate != null) {
                rows.computeIfAbsent(device, DiskIoDetailRow::new).writeBytes = Math.round(rate);
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
        // 汇总吞吐维持「先聚合再求差」口径，与历史数据连续
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
        emitDetail(rows.values(), sink, code(), ts);
    }

    /** 按设备明细行（Linux 与 Windows 采集项共用）。 */
    static final class DiskIoDetailRow {
        final String device;
        Double utilPercent;
        Long readBytes;
        Long writeBytes;

        DiskIoDetailRow(String device) {
            this.device = device;
        }
    }

    /** 序列化按盘明细为 host.diskio.detail 文本指标（按繁忙度倒序）。 */
    static void emitDetail(Iterable<DiskIoDetailRow> rows, HostMetricSink sink, String itemCode, long ts) {
        List<Map<String, Object>> detail = new ArrayList<>();
        for (DiskIoDetailRow r : rows) {
            if (r.utilPercent == null && r.readBytes == null && r.writeBytes == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device", r.device);
            row.put("utilPercent", r.utilPercent);
            row.put("readBytes", r.readBytes);
            row.put("writeBytes", r.writeBytes);
            detail.add(row);
        }
        if (detail.isEmpty()) {
            return;
        }
        detail.sort(Comparator.comparingDouble(
                r -> -(r.get("utilPercent") instanceof Number n ? n.doubleValue() : -1)));
        try {
            sink.addText("host.diskio.detail", HostJson.toJson(detail), ts);
        } catch (Exception e) {
            sink.addItemError(itemCode, "磁盘 IO 明细序列化失败：" + e.getMessage());
        }
    }

    private static boolean isVirtualDevice(String device) {
        if (device == null) {
            return true;
        }
        return device.startsWith("loop") || device.startsWith("ram")
                || device.startsWith("sr") || device.startsWith("fd");
    }
}
