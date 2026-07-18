package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

/** SQL Server Agent 汇总状态及作业级最近运行证据；Express Edition 自动跳过。 */
@Component
public class SqlServerAgentItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "agent_jobs";
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        if (expressEdition(conn)) return;
        long ts = System.currentTimeMillis();
        collectSummary(conn, adapter, sink, ts);
        collectJobs(conn, adapter, sink, ts);
    }

    private static void collectSummary(Connection conn, SqlServerVersionAdapter adapter,
                                       SqlServerMetricSink sink, long ts) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(10);
            try (ResultSet rs = st.executeQuery(adapter.agentHealthSql())) {
                if (!rs.next()) return;
                emit(rs, sink, "sqlserver.agent.job_count", "job_count", ts);
                emit(rs, sink, "sqlserver.agent.disabled_jobs", "disabled_jobs", ts);
                emit(rs, sink, "sqlserver.agent.failed_jobs", "failed_jobs", ts);
                emit(rs, sink, "sqlserver.agent.running_jobs", "running_jobs", ts);
            }
        }
    }

    private static void collectJobs(Connection conn, SqlServerVersionAdapter adapter,
                                    SqlServerMetricSink sink, long ts) throws Exception {
        int consecutiveFailureJobs = 0;
        long maxRunningSeconds = 0;
        StringBuilder snapshot = new StringBuilder();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(15);
            st.setMaxRows(500);
            try (ResultSet rs = st.executeQuery(adapter.agentJobsSql())) {
                while (rs.next()) {
                    String job = rs.getString("job_name");
                    int enabled = rs.getInt("enabled");
                    int status = rs.getInt("status_code");
                    int failures = rs.getInt("consecutive_failures");
                    long runningSeconds = rs.getLong("running_seconds");
                    if (rs.wasNull()) runningSeconds = 0;
                    if (failures > 0) consecutiveFailureJobs++;
                    maxRunningSeconds = Math.max(maxRunningSeconds, runningSeconds);
                    sink.addObject("sqlserver.agent.job_enabled", "agent_job", job, enabled, ts);
                    sink.addObject("sqlserver.agent.job_status_code", "agent_job", job, status, ts);
                    sink.addObject("sqlserver.agent.job_duration_seconds", "agent_job", job,
                            durationSeconds(rs.getInt("run_duration")), ts);
                    sink.addObject("sqlserver.agent.job_consecutive_failures", "agent_job", job, failures, ts);
                    sink.addObject("sqlserver.agent.job_running_seconds", "agent_job", job, runningSeconds, ts);
                    snapshot.append("job=").append(job.replace('|', ' '))
                            .append("|enabled=").append(enabled)
                            .append("|status=").append(status)
                            .append("|last_run=").append(rs.getInt("run_date")).append('/').append(rs.getInt("run_time"))
                            .append("|next_run=").append(rs.getInt("next_run_date")).append('/').append(rs.getInt("next_run_time"))
                            .append("|failed_step=").append(nullSafe(rs.getString("failed_step_name")))
                            .append('\n');
                }
            }
        }
        sink.addNumeric("sqlserver.agent.consecutive_failure_jobs", consecutiveFailureJobs, ts);
        sink.addNumeric("sqlserver.agent.max_running_seconds", maxRunningSeconds, ts);
        sink.addText("sqlserver.agent.job_snapshot", snapshot.toString(), ts);
    }

    static int durationSeconds(int hhmmss) {
        int hours = hhmmss / 10000;
        int minutes = (hhmmss / 100) % 100;
        int seconds = hhmmss % 100;
        return hours * 3600 + minutes * 60 + seconds;
    }

    private static boolean expressEdition(Connection conn) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT CAST(SERVERPROPERTY('EngineEdition') AS int)")) {
            return rs.next() && rs.getInt(1) == 4;
        }
    }

    private static void emit(ResultSet rs, SqlServerMetricSink sink,
                             String metric, String column, long ts) throws Exception {
        double value = rs.getDouble(column);
        if (!rs.wasNull()) sink.addNumeric(metric, value, ts);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.replace('|', ' ');
    }
}