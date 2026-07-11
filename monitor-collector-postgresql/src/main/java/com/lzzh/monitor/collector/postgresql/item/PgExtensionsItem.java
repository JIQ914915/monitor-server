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
 * 采集项：扩展探测（二期 B1，天级）。
 *
 * <p>探测依赖 {@code shared_preload_libraries} 的关键扩展安装状态，供能力检测与巡检使用：
 * <ul>
 *   <li>{@code pg.ext.pg_stat_statements} —— 0=未启用；1=已加载未 CREATE EXTENSION（差最后一步）；
 *       2=已就绪（Top SQL / 指纹分析可用）</li>
 *   <li>{@code pg.ext.pgaudit} —— 同上口径（审计对接为后续阶段，先探测状态）</li>
 * </ul>
 * 状态区分"加载"与"创建"两步：preload 需改配置并重启，CREATE EXTENSION 在线即可，
 * 能力检测页据此给出差异化的安装引导。
 */
@Component
public class PgExtensionsItem implements PgMetricItem {

    public static final String CODE = "pg_extensions";

    private static final String[] WATCHED = {"pg_stat_statements", "pgaudit"};

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        String preload = queryScalar(conn,
                "SELECT setting FROM pg_settings WHERE name = 'shared_preload_libraries'");
        for (String ext : WATCHED) {
            boolean loaded = preload != null && containsToken(preload, ext);
            boolean created = queryScalar(conn,
                    "SELECT extname FROM pg_extension WHERE extname = '" + ext + "'") != null;
            // 2=就绪（已加载且已创建）；1=差一步（只加载未创建，或只创建未加载）；0=未启用
            double status = (loaded && created) ? 2 : (loaded || created) ? 1 : 0;
            sink.addNumeric("pg.ext." + ext, status, ts);
        }
    }

    /** preload 串按逗号拆分匹配，避免子串误命中。 */
    private static boolean containsToken(String csv, String token) {
        for (String part : csv.split(",")) {
            if (part.trim().equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static String queryScalar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
