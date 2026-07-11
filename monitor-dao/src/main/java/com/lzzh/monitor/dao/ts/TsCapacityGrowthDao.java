package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsCapacityGrowthMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 容量增长趋势查询 DAO。
 * <p>读取 {@code capacity_weekly_growth} 视图（V51 创建、V148 修正口径为
 * "每表取当日末次快照后按实例求和"），返回指定实例最近 N 天的日级容量快照与 7 日环比数据。
 * <p>若底层 {@code capacity_object_daily} 物化视图尚未刷新（无数据），则返回空列表。
 */
@Repository
public class TsCapacityGrowthDao {

    private final TsCapacityGrowthMapper mapper;

    public TsCapacityGrowthDao(TsCapacityGrowthMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询指定实例最近 {@code days} 天的容量增长趋势。
     *
     * @param instanceId 实例 ID
     * @param days       查询天数（默认 30）
     * @return 日级容量点列表（按日期升序）
     */
    public List<CapacityGrowthPoint> queryGrowthTrend(Long instanceId, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        List<Map<String, Object>> rows = mapper.selectGrowthTrend(instanceId, Date.valueOf(since));
        List<CapacityGrowthPoint> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            LocalDate day = toLocalDate(row.get("current_day"));
            Number currentBytes = (Number) row.get("current_bytes");
            Number prevWeekBytes = (Number) row.get("prev_week_bytes");
            Number growthBytes = (Number) row.get("growth_bytes");
            Number growthRatePct = (Number) row.get("growth_rate_pct");
            result.add(new CapacityGrowthPoint(
                    day,
                    currentBytes != null ? currentBytes.longValue() : 0L,
                    prevWeekBytes != null ? prevWeekBytes.longValue() : null,
                    growthBytes != null ? growthBytes.longValue() : null,
                    growthRatePct != null ? growthRatePct.doubleValue() : null
            ));
        }
        return result;
    }

    /** JDBC 驱动对 date/timestamptz 派生列可能返回 Date 或 Timestamp，统一转 LocalDate。 */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Date d) return d.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
        throw new IllegalStateException("无法解析容量趋势日期列 current_day：" + value);
    }

    /** 容量增长趋势单日数据点。 */
    public record CapacityGrowthPoint(
            LocalDate day,
            long currentBytes,
            Long prevWeekBytes,
            Long growthBytes,
            Double growthRatePct
    ) {}
}
