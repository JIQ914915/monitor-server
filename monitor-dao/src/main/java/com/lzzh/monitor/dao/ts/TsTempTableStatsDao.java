package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsTempTableStatsMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 临时表今日统计查询 DAO。
 * <p>对 {@code metric_data_1m} 中的 {@code mysql.delta.created_tmp_tables}
 * 和 {@code mysql.delta.created_tmp_disk_tables} 今日数据做 SUM 聚合，
 * 得到"今日累计内存临时表数"和"今日累计磁盘临时表数"。
 * <p>数据由 {@code GlobalStatusItem.EXTRA_DELTA} 每分钟产出周期增量点。
 */
@Repository
public class TsTempTableStatsDao {

    private final TsTempTableStatsMapper mapper;

    public TsTempTableStatsDao(TsTempTableStatsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询今日临时表和慢查询累计统计。
     *
     * @param instanceId 实例 ID
     * @return 统计数据；若今日无数据则各字段为 0
     */
    public TodayStats queryTodayStats(Long instanceId) {
        Map<String, Object> row = mapper.selectTodayStats(instanceId);
        if (row == null || row.isEmpty()) {
            return new TodayStats(0L, 0L, 0L);
        }
        return new TodayStats(
                toLong(row.get("tmp_tables_today")),
                toLong(row.get("tmp_disk_tables_today")),
                toLong(row.get("slow_queries_today"))
        );
    }

    private static long toLong(Object obj) {
        if (obj == null) {
            return 0L;
        }
        return ((Number) obj).longValue();
    }

    /** 今日累计统计数据。 */
    public record TodayStats(
            long tmpTablesToday,
            long tmpDiskTablesToday,
            long slowQueriesToday
    ) {}
}
