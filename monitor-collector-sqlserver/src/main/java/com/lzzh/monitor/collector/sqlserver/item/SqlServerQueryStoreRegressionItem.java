package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Query Store 最近一小时相对前七天的计划变化和耗时回退证据。 */
@Component
public class SqlServerQueryStoreRegressionItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "query_store_regression";
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        if (!adapter.supportsQueryStore()) return;
        long ts = System.currentTimeMillis();
        String originalDatabase = currentDatabase(conn);
        int enabledDatabases = 0;
        int changedQueries = 0;
        double maxRegressionRatio = 0;
        StringBuilder snapshot = new StringBuilder();
        try {
            for (String database : accessibleUserDatabases(conn)) {
                try {
                    useDatabase(conn, database);
                    if (!queryStoreEnabled(conn, adapter)) continue;
                    enabledDatabases++;
                    try (Statement st = conn.createStatement()) {
                        st.setQueryTimeout(20);
                        st.setMaxRows(50);
                        try (ResultSet rs = st.executeQuery(adapter.queryStoreRegressionSql())) {
                            while (rs.next()) {
                                String digest = rs.getString("digest");
                                String query = database + "/" + digest;
                                int planCount = rs.getInt("current_plan_count");
                                boolean newPlan = rs.getInt("has_new_plan") == 1;
                                double ratio = rs.getDouble("regression_ratio");
                                boolean ratioNull = rs.wasNull();
                                boolean changed = newPlan || planCount > 1;
                                if (changed) changedQueries++;
                                sink.addObject("sqlserver.query_store.plan_changed", "query", query, changed ? 1 : 0, ts);
                                sink.addObject("sqlserver.query_store.current_plan_count", "query", query, planCount, ts);
                                if (!ratioNull) {
                                    maxRegressionRatio = Math.max(maxRegressionRatio, ratio);
                                    sink.addObject("sqlserver.query_store.regression_ratio", "query", query, ratio, ts);
                                }
                                snapshot.append("query=").append(query)
                                        .append("|executions=").append(rs.getLong("current_executions"))
                                        .append("|plans=").append(planCount)
                                        .append("|new_plan=").append(newPlan ? 1 : 0)
                                        .append("|ratio=").append(ratioNull ? "" : ratio)
                                        .append('\n');
                            }
                        }
                    }
                } catch (SQLException e) {
                    sink.addItemError(code(), database + ": " + e.getMessage());
                }
            }
        } finally {
            useDatabase(conn, originalDatabase);
        }
        sink.addNumeric("sqlserver.query_store.enabled_database_count", enabledDatabases, ts);
        sink.addNumeric("sqlserver.query_store.plan_changed_query_count", changedQueries, ts);
        sink.addNumeric("sqlserver.query_store.max_regression_ratio", maxRegressionRatio, ts);
        sink.addText("sqlserver.query_store.regression_snapshot", snapshot.toString(), ts);
    }

    private static boolean queryStoreEnabled(Connection conn, SqlServerVersionAdapter adapter) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(adapter.queryStoreCapabilitySql())) {
            if (!rs.next()) return false;
            int state = rs.getInt("actual_state");
            return !rs.wasNull() && state != 0;
        }
    }

    private static List<String> accessibleUserDatabases(Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT name FROM sys.databases
                 WHERE database_id>4 AND state_desc='ONLINE' AND source_database_id IS NULL
                   AND HAS_DBACCESS(name)=1 ORDER BY name
                """)) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    private static String currentDatabase(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT DB_NAME()")) {
            if (!rs.next()) throw new SQLException("无法读取当前数据库上下文");
            return rs.getString(1);
        }
    }

    private static void useDatabase(Connection conn, String database) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("USE [" + database.replace("]", "]]" ) + "]");
        }
    }
}