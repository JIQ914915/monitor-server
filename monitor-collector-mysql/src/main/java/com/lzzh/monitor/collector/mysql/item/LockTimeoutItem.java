package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：行锁等待超时与死锁次数（内置规则 builtin.lock_timeout.warning /
 * builtin.deadlock.warning 的数据来源）。
 *
 * <p>数据源：{@code information_schema.INNODB_METRICS} 的 lock 模块计数器
 * （默认启用，MySQL 5.6+ 各版本均可用），均为自实例启动以来的累积值，
 * 经 {@link CounterDeltaStore} 做差得到本采集周期增量：
 * <ul>
 *   <li>{@code lock_timeouts} → {@code mysql.innodb.lock_timeout_count}：本周期行锁等待超时次数；</li>
 *   <li>{@code lock_deadlocks} → {@code mysql.innodb.deadlock_count}：本周期死锁次数。</li>
 * </ul>
 * 首次采样 / 实例重启回绕时跳过本周期（与其他 delta 指标一致）。
 */
@Component
public class LockTimeoutItem implements MySqlMetricItem {

    private static final String CODE = "lock_timeout";

    private static final String SQL =
            "SELECT name, count FROM information_schema.INNODB_METRICS "
                    + "WHERE name IN ('lock_timeouts', 'lock_deadlocks')";

    private final CounterDeltaStore deltaStore;

    public LockTimeoutItem(CounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        MySqlVersionAdapter adapter, MetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(SQL)) {
                // 计数器被关闭（innodb_monitor_disable）时无对应行，跳过该指标，不产出
                while (rs.next()) {
                    String name = rs.getString(1);
                    long cumulative = rs.getLong(2);
                    Long delta = deltaStore.delta(request.getInstanceId(), "innodb_metrics." + name, cumulative, ts);
                    if (delta == null) {
                        continue;
                    }
                    if ("lock_timeouts".equals(name)) {
                        sink.addNumeric("mysql.innodb.lock_timeout_count", (double) delta, ts);
                    } else if ("lock_deadlocks".equals(name)) {
                        sink.addNumeric("mysql.innodb.deadlock_count", (double) delta, ts);
                    }
                }
            }
        }
    }
}
