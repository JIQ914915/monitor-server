package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 当前阻塞链的等待时长、链深和根阻塞者。 */
@Component
public class SqlServerBlockingItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "blocking_chains";
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        Map<Integer, BlockingRow> rows = new LinkedHashMap<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            st.setMaxRows(500);
            try (ResultSet rs = st.executeQuery(adapter.blockingChainSql())) {
                while (rs.next()) {
                    int waiting = rs.getInt("waiting_pid");
                    rows.putIfAbsent(waiting, new BlockingRow(waiting, rs.getInt("blocking_pid"),
                            Math.max(0, rs.getLong("wait_age_secs")), rs.getString("locked_table"),
                            rs.getString("locked_type"), rs.getString("waiting_query"),
                            rs.getString("blocking_query")));
                }
            }
        }

        long maxWait = 0;
        int maxDepth = 0;
        Map<Integer, Integer> affectedByRoot = new HashMap<>();
        StringBuilder snapshot = new StringBuilder();
        for (BlockingRow row : rows.values()) {
            int root = rootBlocker(row.waitingPid(), rows);
            int depth = chainDepth(row.waitingPid(), rows);
            maxWait = Math.max(maxWait, row.waitSeconds());
            maxDepth = Math.max(maxDepth, depth);
            affectedByRoot.merge(root, 1, Integer::sum);
            sink.addObject("sqlserver.blocking.wait_seconds", "session", String.valueOf(row.waitingPid()), row.waitSeconds(), ts);
            sink.addObject("sqlserver.blocking.chain_depth", "session", String.valueOf(row.waitingPid()), depth, ts);
            snapshot.append("waiting=").append(row.waitingPid())
                    .append("|blocking=").append(row.blockingPid())
                    .append("|root=").append(root)
                    .append("|depth=").append(depth)
                    .append("|seconds=").append(row.waitSeconds())
                    .append("|object=").append(nullSafe(row.lockedTable()))
                    .append("|wait=").append(nullSafe(row.lockedType()))
                    .append("|waiting_sql=").append(SqlServerSqlRedactor.redact(row.waitingQuery()))
                    .append("|blocking_sql=").append(SqlServerSqlRedactor.redact(row.blockingQuery()))
                    .append('\n');
        }
        affectedByRoot.forEach((root, affected) -> sink.addObject(
                "sqlserver.blocking.root_affected_sessions", "session", String.valueOf(root), affected, ts));
        sink.addNumeric("sqlserver.blocking.max_wait_seconds", maxWait, ts);
        sink.addNumeric("sqlserver.blocking.max_chain_depth", maxDepth, ts);
        sink.addNumeric("sqlserver.blocking.root_blocker_count", affectedByRoot.size(), ts);
        sink.addText("sqlserver.blocking.snapshot", snapshot.toString(), ts);
    }

    static int rootBlocker(int waitingPid, Map<Integer, BlockingRow> rows) {
        BlockingRow current = rows.get(waitingPid);
        if (current == null) return waitingPid;
        Set<Integer> visited = new HashSet<>();
        visited.add(waitingPid);
        int blocker = current.blockingPid();
        while (rows.containsKey(blocker) && visited.add(blocker)) {
            blocker = rows.get(blocker).blockingPid();
        }
        return blocker;
    }

    static int chainDepth(int waitingPid, Map<Integer, BlockingRow> rows) {
        Set<Integer> visited = new HashSet<>();
        int current = waitingPid;
        int depth = 0;
        while (rows.containsKey(current) && visited.add(current)) {
            current = rows.get(current).blockingPid();
            depth++;
        }
        return depth;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.replace('|', ' ');
    }

    record BlockingRow(int waitingPid, int blockingPid, long waitSeconds, String lockedTable,
                       String lockedType, String waitingQuery, String blockingQuery) {}
}