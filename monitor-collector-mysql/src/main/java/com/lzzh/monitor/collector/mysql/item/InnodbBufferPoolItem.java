package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * 采集项：InnoDB Buffer Pool 派生指标（§P1-5 / §P2-3）。
 *
 * <p>不额外发起 SQL，复用 {@link GlobalStatusItem} 在同轮采集中写入的
 * {@link MetricSink#getGlobalStatusSnapshot()} 全量快照，零额外查询开销。
 *
 * <p>必须在 GlobalStatusItem 之后执行（item 按 code 字母序排列，
 * "global_status" < "innodb_buffer_pool"，顺序天然保证）。
 *
 * <p>产出五条分钟级 gauge 指标：
 * <ul>
 *   <li>{@code mysql.innodb.buffer_pool_hit_rate}：Buffer Pool 读命中率（%），
 *       = (1 - Innodb_buffer_pool_reads / Innodb_buffer_pool_read_requests) × 100</li>
 *   <li>{@code mysql.innodb.dirty_page_ratio}：脏页比例（%），
 *       = Innodb_buffer_pool_pages_dirty / Innodb_buffer_pool_pages_total × 100</li>
 *   <li>{@code mysql.innodb.buffer_pool_usage}：Buffer Pool 页使用率（%），
 *       = (total - free) / total × 100</li>
 *   <li>{@code mysql.innodb.buffer_pool_bytes_data}：Buffer Pool 数据字节数（Innodb_buffer_pool_bytes_data，字节），
 *       用于展示"已用 GB"，需配合 innodb_buffer_pool_size（VariablesItem 1d）展示占比。</li>
 *   <li>{@code mysql.innodb.buffer_pool_bytes_dirty}：脏页字节数（Innodb_buffer_pool_bytes_dirty，字节）。</li>
 * </ul>
 *
 * <p>注意：命中率基于服务启动以来的累积计数器，反映"历史有效命中率"，是 DBA 运维中最常用的 Pool 健康指标。
 */
@Component
public class InnodbBufferPoolItem implements MySqlMetricItem {

    private static final String CODE = "innodb_buffer_pool";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        MySqlVersionAdapter adapter, MetricSink sink) throws SQLException {
        Map<String, Long> snapshot = sink.getGlobalStatusSnapshot();
        if (snapshot == null) {
            // GlobalStatusItem 尚未执行（异常情况），跳过
            return;
        }
        long ts = System.currentTimeMillis();

        // Buffer Pool 读命中率：基于累积计数器（反映历史整体命中水平）
        Long readRequests = snapshot.get("Innodb_buffer_pool_read_requests");
        Long physicalReads = snapshot.get("Innodb_buffer_pool_reads");
        if (readRequests != null && readRequests > 0 && physicalReads != null) {
            double hitRate = (1.0 - (double) physicalReads / readRequests) * 100.0;
            sink.addNumeric("mysql.innodb.buffer_pool_hit_rate", round2(Math.max(0, Math.min(100, hitRate))), ts);
        }

        // 脏页比例（当前瞬时 gauge）
        Long totalPages = snapshot.get("Innodb_buffer_pool_pages_total");
        Long dirtyPages = snapshot.get("Innodb_buffer_pool_pages_dirty");
        if (totalPages != null && totalPages > 0 && dirtyPages != null) {
            double dirtyRatio = (double) dirtyPages / totalPages * 100.0;
            sink.addNumeric("mysql.innodb.dirty_page_ratio", round2(dirtyRatio), ts);
        }

        // Buffer Pool 页使用率（当前瞬时 gauge）
        Long freePages = snapshot.get("Innodb_buffer_pool_pages_free");
        if (totalPages != null && totalPages > 0 && freePages != null) {
            double usageRatio = (1.0 - (double) freePages / totalPages) * 100.0;
            sink.addNumeric("mysql.innodb.buffer_pool_usage", round2(Math.max(0, Math.min(100, usageRatio))), ts);
        }

        // Buffer Pool 数据页字节数（绝对已用容量，供前端展示"已用 GB"）
        Long bytesData = snapshot.get("Innodb_buffer_pool_bytes_data");
        if (bytesData != null) {
            sink.addNumeric("mysql.innodb.buffer_pool_bytes_data", (double) bytesData, ts);
        }
        // 脏页字节数
        Long bytesDirty = snapshot.get("Innodb_buffer_pool_bytes_dirty");
        if (bytesDirty != null) {
            sink.addNumeric("mysql.innodb.buffer_pool_bytes_dirty", (double) bytesDirty, ts);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
