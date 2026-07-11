package com.lzzh.monitor.collector.spi.version;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 版本路由：精确匹配 → 就近向下版本 → null。
 * 避免新出现的小版本因无精确适配而直接报错（§5.8 版本兼容回退）。
 *
 * @param <T> 具体数据库的版本适配器类型
 */
public class VersionResolver<T extends VersionAdapter> {

    /** 以可比较的版本键有序存放。 */
    private final TreeMap<int[], T> adapters = new TreeMap<>(versionKeyComparator());

    public void register(T adapter) {
        adapters.put(parse(adapter.version()), adapter);
    }

    /** 解析请求版本对应的适配器：精确→就近向下→null。 */
    public T resolve(String version) {
        int[] key = parse(version);
        T exact = adapters.get(key);
        if (exact != null) {
            return exact;
        }
        Map.Entry<int[], T> floor = adapters.floorEntry(key);
        return floor != null ? floor.getValue() : null;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return new int[]{0, 0, 0};
        }
        String[] parts = version.split("\\.");
        int[] key = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                key[i] = Integer.parseInt(parts[i].replaceAll("\\D", ""));
            } catch (NumberFormatException ignore) {
                key[i] = 0;
            }
        }
        return key;
    }

    private static Comparator<int[]> versionKeyComparator() {
        return (a, b) -> {
            for (int i = 0; i < 3; i++) {
                int cmp = Integer.compare(a[i], b[i]);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        };
    }
}
