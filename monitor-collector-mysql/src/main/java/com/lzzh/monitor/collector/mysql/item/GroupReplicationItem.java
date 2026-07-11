package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：MySQL Group Replication 成员状态（内置规则 builtin.gr.member_state.critical 的数据来源）。
 *
 * <p>数据源：{@code performance_schema.replication_group_members}（仅 8.0+ 适配器启用），
 * 取本实例（MEMBER_ID = @@server_uuid）的 MEMBER_STATE：
 * <ul>
 *   <li>ONLINE → 1；RECOVERING / OFFLINE / ERROR / UNREACHABLE → 0。</li>
 *   <li>未配置 MGR（表无本机行）时不产出指标 → 规则评估 metric_missing，不误报。</li>
 * </ul>
 *
 * <p>产出分钟级指标 {@code mysql.gr.member_state}。
 */
@Component
public class GroupReplicationItem implements MySqlMetricItem {

    private static final String CODE = "group_replication";

    private static final String SQL =
            "SELECT MEMBER_STATE FROM performance_schema.replication_group_members "
                    + "WHERE MEMBER_ID = @@server_uuid";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        MySqlVersionAdapter adapter, MetricSink sink) throws SQLException {
        if (!adapter.supportsGroupReplication()) {
            return;
        }
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(SQL)) {
                if (!rs.next()) {
                    // 未启用 MGR：不产出指标，规则侧按指标缺失处理
                    return;
                }
                String state = rs.getString(1);
                double value = "ONLINE".equalsIgnoreCase(state) ? 1.0 : 0.0;
                sink.addNumeric("mysql.gr.member_state", value, ts);
            }
        }
    }
}
