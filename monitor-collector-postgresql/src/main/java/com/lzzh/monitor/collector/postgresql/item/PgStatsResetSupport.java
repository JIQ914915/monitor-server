package com.lzzh.monitor.collector.postgresql.item;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/** PostgreSQL 累计统计重置时间的统一解析。 */
final class PgStatsResetSupport {
    private PgStatsResetSupport() {}

    static Double ageSeconds(ResultSet rs, String column, long nowMillis) throws SQLException {
        Timestamp resetAt = rs.getTimestamp(column);
        if (resetAt == null) return null;
        Instant now = Instant.ofEpochMilli(nowMillis);
        return (double) Math.max(0, Duration.between(resetAt.toInstant(), now).toSeconds());
    }
}
