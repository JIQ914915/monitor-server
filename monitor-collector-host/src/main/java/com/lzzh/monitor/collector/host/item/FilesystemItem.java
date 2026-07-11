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
 * 磁盘空间与文件系统状态：node_filesystem_*（按挂载点，排除虚拟文件系统）。
 * 产出：host.disk.usage_max / host.disk.inode_usage_max / host.disk.readonly_count
 *      + 文本明细 host.disk.mount_detail（各挂载点使用率 JSON，变更覆盖存储）。
 */
@Component
public class FilesystemItem implements HostMetricItem {

    /** 虚拟/临时文件系统类型，不纳入磁盘空间统计。 */
    private static final Set<String> EXCLUDED_FSTYPES = Set.of(
            "tmpfs", "devtmpfs", "overlay", "squashfs", "ramfs", "aufs",
            "proc", "procfs", "sysfs", "cgroup", "cgroup2", "nsfs", "autofs",
            "iso9660", "rpc_pipefs", "tracefs", "debugfs", "configfs", "mqueue",
            "hugetlbfs", "bpf", "pstore", "securityfs", "devpts", "binfmt_misc",
            "fuse.lxcfs", "selinuxfs", "fusectl");

    @Override
    public String code() {
        return "host.disk";
    }

    @Override
    public Set<String> families() {
        return Set.of("node_filesystem_size_bytes", "node_filesystem_avail_bytes",
                "node_filesystem_files", "node_filesystem_files_free", "node_filesystem_readonly");
    }

    @Override
    public void collect(Long hostId, PromSnapshot snapshot, DeltaCache deltaCache, HostMetricSink sink) {
        long ts = snapshot.timestampMillis();
        Map<String, Double> size = byMount(snapshot, "node_filesystem_size_bytes");
        Map<String, Double> avail = byMount(snapshot, "node_filesystem_avail_bytes");
        Map<String, Double> files = byMount(snapshot, "node_filesystem_files");
        Map<String, Double> filesFree = byMount(snapshot, "node_filesystem_files_free");
        Map<String, Double> readonly = byMount(snapshot, "node_filesystem_readonly");
        Map<String, String> fstypes = fstypeByMount(snapshot);
        if (size.isEmpty()) {
            return;
        }

        double usageMax = -1, inodeUsageMax = -1;
        int readonlyCount = 0;
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Map.Entry<String, Double> e : size.entrySet()) {
            String mount = e.getKey();
            double total = e.getValue();
            Double availBytes = avail.get(mount);
            if (total <= 0 || availBytes == null) {
                continue;
            }
            double usage = CpuItem.clampPercent((1.0 - availBytes / total) * 100.0);
            usageMax = Math.max(usageMax, usage);

            Double inodeTotal = files.get(mount);
            Double inodeFree = filesFree.get(mount);
            Double inodeUsage = null;
            if (inodeTotal != null && inodeTotal > 0 && inodeFree != null) {
                inodeUsage = CpuItem.clampPercent((1.0 - inodeFree / inodeTotal) * 100.0);
                inodeUsageMax = Math.max(inodeUsageMax, inodeUsage);
            }
            if (readonly.getOrDefault(mount, 0.0) >= 1.0) {
                readonlyCount++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mount", mount);
            row.put("fstype", fstypes.get(mount));
            row.put("totalBytes", (long) total);
            row.put("availBytes", availBytes.longValue());
            row.put("usagePercent", CpuItem.round2(usage));
            row.put("inodeUsagePercent", inodeUsage == null ? null : CpuItem.round2(inodeUsage));
            row.put("readonly", readonly.getOrDefault(mount, 0.0) >= 1.0);
            detail.add(row);
        }
        if (usageMax >= 0) {
            sink.addNumeric("host.disk.usage_max", CpuItem.round2(usageMax), ts);
        }
        if (inodeUsageMax >= 0) {
            sink.addNumeric("host.disk.inode_usage_max", CpuItem.round2(inodeUsageMax), ts);
        }
        sink.addNumeric("host.disk.readonly_count", readonlyCount, ts);

        if (!detail.isEmpty()) {
            detail.sort(Comparator.comparingDouble(
                    r -> -((Number) r.get("usagePercent")).doubleValue()));
            try {
                sink.addText("host.disk.mount_detail", HostJson.toJson(detail), ts);
            } catch (Exception e) {
                sink.addItemError(code(), "挂载点明细序列化失败：" + e.getMessage());
            }
        }
    }

    /** family → (mountpoint → value)，排除虚拟文件系统；同挂载点多设备取首个。 */
    private static Map<String, Double> byMount(PromSnapshot snapshot, String family) {
        Map<String, Double> result = new HashMap<>();
        for (PromSample s : snapshot.samples(family)) {
            String mount = s.label("mountpoint");
            String fstype = s.label("fstype");
            if (mount == null || (fstype != null && EXCLUDED_FSTYPES.contains(fstype))) {
                continue;
            }
            result.putIfAbsent(mount, s.value());
        }
        return result;
    }

    private static Map<String, String> fstypeByMount(PromSnapshot snapshot) {
        Map<String, String> result = new HashMap<>();
        for (PromSample s : snapshot.samples("node_filesystem_size_bytes")) {
            String mount = s.label("mountpoint");
            String fstype = s.label("fstype");
            if (mount != null && fstype != null && !EXCLUDED_FSTYPES.contains(fstype)) {
                result.putIfAbsent(mount, fstype);
            }
        }
        return result;
    }
}
