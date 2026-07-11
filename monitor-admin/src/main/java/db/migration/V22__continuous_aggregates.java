package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 连续聚合降采样 1m → 1h → 1d（§21.2.3，第 3 步）。
 * <p>用 Java 迁移而非 SQL，原因有二：
 * <ol>
 *   <li>{@code CREATE MATERIALIZED VIEW ... WITH (timescaledb.continuous)} 不能在事务块内执行，
 *       故声明 {@link #canExecuteInTransaction()} 返回 false（Flyway 以自动提交模式执行）；</li>
 *   <li>连续聚合无法用 SQL 的 {@code DO $$ IF EXISTS timescaledb} 条件创建；此处在运行时检测扩展，
 *       未安装 TimescaleDB（如纯 PG 开发库）则跳过，避免破坏启动。</li>
 * </ol>
 * 趋势查询命中这两个连续聚合（实时看 metric_data_1m，历史看 1h/1d 聚合），扫描量小、响应快。
 */
public class V22__continuous_aggregates extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V22__continuous_aggregates.class);

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        if (!timescaleInstalled(conn)) {
            log.warn("未检测到 TimescaleDB 扩展，跳过连续聚合创建（纯 PG 环境）。");
            return;
        }
        for (String sql : STATEMENTS) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
        log.info("连续聚合 metric_data_1h_cagg / metric_data_1d_cagg 创建完成。");
    }

    private boolean timescaleInstalled(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'timescaledb'")) {
            return rs.next();
        }
    }

    private static final String[] STATEMENTS = {
            // 1m → 1h 连续聚合（小时级 平均/最大/最小/末值）
            "CREATE MATERIALIZED VIEW IF NOT EXISTS metric_data_1h_cagg "
                    + "WITH (timescaledb.continuous) AS "
                    + "SELECT instance_id, metric_code, "
                    + "time_bucket(INTERVAL '1 hour', collect_time) AS bucket, "
                    + "avg(value) AS avg_value, max(value) AS max_value, "
                    + "min(value) AS min_value, last(value, collect_time) AS last_value "
                    + "FROM metric_data_1m "
                    + "GROUP BY instance_id, metric_code, time_bucket(INTERVAL '1 hour', collect_time) "
                    + "WITH NO DATA",
            "SELECT add_continuous_aggregate_policy('metric_data_1h_cagg', "
                    + "start_offset => INTERVAL '3 hours', end_offset => INTERVAL '1 hour', "
                    + "schedule_interval => INTERVAL '1 hour', if_not_exists => TRUE)",
            // 1h → 1d 分层连续聚合（基于 1h 聚合再聚合）
            "CREATE MATERIALIZED VIEW IF NOT EXISTS metric_data_1d_cagg "
                    + "WITH (timescaledb.continuous) AS "
                    + "SELECT instance_id, metric_code, "
                    + "time_bucket(INTERVAL '1 day', bucket) AS bucket, "
                    + "avg(avg_value) AS avg_value, max(max_value) AS max_value, "
                    + "min(min_value) AS min_value, last(last_value, bucket) AS last_value "
                    + "FROM metric_data_1h_cagg "
                    + "GROUP BY instance_id, metric_code, time_bucket(INTERVAL '1 day', bucket) "
                    + "WITH NO DATA",
            "SELECT add_continuous_aggregate_policy('metric_data_1d_cagg', "
                    + "start_offset => INTERVAL '3 days', end_offset => INTERVAL '1 day', "
                    + "schedule_interval => INTERVAL '1 day', if_not_exists => TRUE)",
            // 连续聚合自身的保留策略（对齐小时级 180 天 / 天级 2 年）
            "SELECT add_retention_policy('metric_data_1h_cagg', INTERVAL '180 days', if_not_exists => TRUE)",
            "SELECT add_retention_policy('metric_data_1d_cagg', INTERVAL '2 years', if_not_exists => TRUE)"
    };
}
