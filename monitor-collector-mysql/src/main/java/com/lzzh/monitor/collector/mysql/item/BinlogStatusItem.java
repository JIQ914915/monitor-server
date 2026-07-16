package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：Binlog 磁盘占用（§P2-3，小时级）。
 *
 * <p>执行 {@code SHOW BINARY LOGS}，累加所有 binlog 文件大小，
 * 产出 {@code mysql.binlog.total_bytes}（当前所有 binlog 文件的总磁盘占用）。
 *
 * <p>若 binlog 未启用（{@code log_bin=OFF}）则 {@code SHOW BINARY LOGS} 返回空集或报错，
 * 采集结果为 0，由上层 item 异常机制捕获后静默处理。
 *
 * <p>所有版本（5.6/5.7/8.0）均支持该语句（需要 {@code REPLICATION CLIENT} 或 {@code SUPER} 权限）。
 */
@Component
public class BinlogStatusItem implements MySqlMetricItem {

    private static final Logger log = LoggerFactory.getLogger(BinlogStatusItem.class);
    private static final String CODE = "binlog_status";
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        MySqlVersionAdapter adapter, MetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();
        long totalBytes = 0;
        int fileCount = 0;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SHOW BINARY LOGS")) {
                // 结果集：Log_name, File_size [, Encrypted（8.0+）]
                while (rs.next()) {
                    totalBytes += rs.getLong(2);
                    fileCount++;
                }
            }
        } catch (SQLException e) {
            // log_bin=OFF 时 MySQL 报 "You are not using binary logging"（error 1381）
            // 属于预期情况，记录 debug 并以 0 字节上报，不向上传播
            if (e.getErrorCode() == 1381 || (e.getMessage() != null
                    && e.getMessage().contains("binary logging"))) {
                log.debug("实例 {} Binlog 未启用，跳过 binlog 大小采集", request.getInstanceId());
                sink.addNumeric("mysql.binlog.total_bytes", 0, ts);
                return;
            }
            throw e;
        }
        sink.addNumeric("mysql.binlog.total_bytes", totalBytes, ts);
        sink.addNumeric("mysql.capacity.binlog_file_count", fileCount, ts);
    }
}
