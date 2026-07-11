package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：事务 ID（XID）回卷风险（小时级）。
 * <ul>
 *   <li>pg.xid.age_max：全部库中 datfrozenxid 最大年龄；</li>
 *   <li>pg.xid.wraparound_pct：距强制停写上限（20 亿）的消耗百分比，
 *       达到 autovacuum_freeze_max_age（默认 2 亿，约 10%）后会触发强制 freeze。</li>
 * </ul>
 * XID 回卷是 PG 特有的高危风险：耗尽后实例拒绝写入，只能单用户模式修复，必须提前预警。
 */
@Component
public class PgXidAgeItem implements PgMetricItem {

    public static final String CODE = "pg_xid";

    /** XID 强制停写上限约 2^31 - 300 万，按 20 亿计算消耗百分比。 */
    private static final double XID_STOP_LIMIT = 2_000_000_000.0;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(
                    "SELECT MAX(age(datfrozenxid)) AS age_max FROM pg_database")) {
                if (rs.next()) {
                    double ageMax = rs.getDouble("age_max");
                    sink.addNumeric("pg.xid.age_max", ageMax, ts);
                    sink.addNumeric("pg.xid.wraparound_pct", ageMax * 100.0 / XID_STOP_LIMIT, ts);
                }
            }
        }
    }
}
