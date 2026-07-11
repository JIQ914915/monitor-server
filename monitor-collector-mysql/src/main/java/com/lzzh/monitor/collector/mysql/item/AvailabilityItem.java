package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 采集项：实例可用性探测（§P2-4）。
 *
 * <p>连接建立成功则此 item 被执行，直接向 sink 写入 {@code mysql.availability = 1}。
 * 当连接失败时，本 item 不会执行；{@link com.lzzh.monitor.collector.runner.CollectRunner}
 * 会在捕获连接异常后补写 {@code mysql.availability = 0}，并按连续失败次数联动更新
 * {@code db_instance.status} 为 {@code abnormal}（恢复后还原为 {@code normal}）。
 *
 * <p>1 = 实例可达，0 = 实例不可达（连接超时/拒绝/认证失败）。
 */
@Component
public class AvailabilityItem implements MySqlMetricItem {

    private static final String CODE = "availability";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        MySqlVersionAdapter adapter, MetricSink sink) throws SQLException {
        // 仅需确认连接存活即可，不执行额外 SQL
        sink.addNumeric("mysql.availability", 1.0, System.currentTimeMillis());
    }
}
