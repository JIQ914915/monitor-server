package com.lzzh.monitor.collector.host;

import com.lzzh.monitor.collector.host.item.HostMetricItem;
import com.lzzh.monitor.collector.host.prom.PromSnapshot;
import com.lzzh.monitor.collector.host.prom.PromTextParser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 主机指标采集器：拉取 node_exporter /metrics → 按采集项白名单解析 → 逐项加工产出 host.* 指标。
 * <p>与 MySQL 采集链路（DatabaseCollector SPI）平行的独立通道：主机不走 JDBC/版本适配，
 * 结构上保持「Collector 聚合 + Item 分项」的同构拆分。
 */
@Component
public class HostCollector {

    private static final Logger log = LoggerFactory.getLogger(HostCollector.class);

    @Resource
    private NodeExporterClient client;
    @Resource
    private DeltaCache deltaCache;
    @Resource
    private List<HostMetricItem> items;
    /** 全部采集项所需的 metric family 白名单（解析时其余行直接跳过）。 */
    private Set<String> allFamilies;

    @PostConstruct
    void init() {
        Set<String> families = new HashSet<>();
        items.forEach(i -> families.addAll(i.families()));
        this.allFamilies = Set.copyOf(families);
    }

    /**
     * 采集一台主机（exporter 通道）。
     *
     * @param hostId       主机 ID（delta 基线维度）
     * @param ip           主机 IP/域名
     * @param exporterPort exporter 端口
     * @param exporterPath exporter 指标路径
     */
    public HostCollectResult collect(Long hostId, String ip, int exporterPort, String exporterPath) {
        String body;
        try {
            body = client.fetch(ip, exporterPort, exporterPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HostCollectResult.fail("采集被中断");
        } catch (Exception e) {
            return HostCollectResult.fail("exporter 拉取失败：" + rootMessage(e));
        }
        PromSnapshot snapshot = PromTextParser.parse(body, allFamilies);
        if (snapshot.families().isEmpty()) {
            return HostCollectResult.fail("exporter 响应中未解析到任何主机指标，请确认该端点为 node_exporter（Linux）或 windows_exporter（Windows）的 /metrics");
        }
        HostMetricSink sink = new HostMetricSink();
        for (HostMetricItem item : items) {
            try {
                item.collect(hostId, snapshot, deltaCache, sink);
            } catch (Exception e) {
                log.warn("主机 {} 采集项 {} 加工失败", hostId, item.code(), e);
                sink.addItemError(item.code(), rootMessage(e));
            }
        }
        return HostCollectResult.ok(sink.numeric(), sink.text(), sink.itemErrors());
    }

    /** 主机删除/暂停时清理 delta 基线。 */
    public void evictBaseline(Long hostId) {
        deltaCache.evict(hostId);
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
