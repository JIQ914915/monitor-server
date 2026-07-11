package com.lzzh.monitor.collector.host.item;

import com.lzzh.monitor.collector.host.DeltaCache;
import com.lzzh.monitor.collector.host.HostJson;
import com.lzzh.monitor.collector.host.HostMetricSink;
import com.lzzh.monitor.collector.host.prom.PromSample;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Windows 磁盘空间：windows_logical_disk_size_bytes / free_bytes（按盘符 volume）。
 * 产出：host.disk.usage_max + 文本明细 host.disk.mount_detail（盘符使用率 JSON）。
 * Windows 无 inode / 只读挂载语义，host.disk.inode_usage_max / readonly_count 不产出。
 */
@Component
public class WindowsDiskItem implements HostMetricItem {

    @Override
    public String code() {
        return "host.disk.windows";
    }

    @Override
    public Set<String> families() {
        return Set.of("windows_logical_disk_size_bytes", "windows_logical_disk_free_bytes");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        Map<String, Double> size = byVolume(snapshot, "windows_logical_disk_size_bytes");
        Map<String, Double> free = byVolume(snapshot, "windows_logical_disk_free_bytes");
        if (size.isEmpty()) {
            return;
        }
        long ts = snapshot.timestampMillis();
        double usageMax = -1;
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Map.Entry<String, Double> e : size.entrySet()) {
            String volume = e.getKey();
            double total = e.getValue();
            Double freeBytes = free.get(volume);
            if (total <= 0 || freeBytes == null) {
                continue;
            }
            double usage = CpuItem.clampPercent((1.0 - freeBytes / total) * 100.0);
            usageMax = Math.max(usageMax, usage);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mount", volume);
            row.put("fstype", "NTFS");
            row.put("totalBytes", (long) total);
            row.put("availBytes", freeBytes.longValue());
            row.put("usagePercent", CpuItem.round2(usage));
            row.put("inodeUsagePercent", null);
            row.put("readonly", false);
            detail.add(row);
        }
        if (usageMax >= 0) {
            sink.addNumeric("host.disk.usage_max", CpuItem.round2(usageMax), ts);
        }
        if (!detail.isEmpty()) {
            detail.sort(Comparator.comparingDouble(r -> -((Number) r.get("usagePercent")).doubleValue()));
            try {
                sink.addText("host.disk.mount_detail", HostJson.toJson(detail), ts);
            } catch (Exception e) {
                sink.addItemError(code(), "盘符明细序列化失败：" + e.getMessage());
            }
        }
    }

    /** family → (volume → value)，排除 _Total 汇总行与无盘符的系统保留卷。 */
    static Map<String, Double> byVolume(PromSnapshot snapshot, String family) {
        Map<String, Double> result = new HashMap<>();
        for (PromSample s : snapshot.samples(family)) {
            String volume = s.label("volume");
            if (isExcludedVolume(volume)) {
                continue;
            }
            result.putIfAbsent(volume, s.value());
        }
        return result;
    }

    static boolean isExcludedVolume(String volume) {
        if (volume == null || "_Total".equalsIgnoreCase(volume)) {
            return true;
        }
        // HarddiskVolumeN：无盘符的系统保留/恢复分区，容量小且必然接近满，纳入会持续误报磁盘不足
        return volume.startsWith("HarddiskVolume");
    }
}
