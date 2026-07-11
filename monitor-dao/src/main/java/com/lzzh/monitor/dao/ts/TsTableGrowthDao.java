package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsTableGrowthMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单表周增长趋势查询 DAO。
 * <p>基于 {@code metric_capacity_object} 的小时级快照，
 * 对每个表取当前最新值与 7 天前最近快照做差值，得到单表周环比增长数据。
 * <p>典型用途：实时概况「资源 Tab」表空间 Top N 列表中的周增长率列。
 */
@Repository
public class TsTableGrowthDao {

    private final TsTableGrowthMapper mapper;

    public TsTableGrowthDao(TsTableGrowthMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询表级周环比 Top N（按增长字节数降序）。
     *
     * @param instanceId 实例 ID
     * @param metricCode 容量指标编码，如 {@code capacity.total_size_bytes}
     * @param limit      返回条数（1~200）
     * @return 周环比列表，无上周数据的表 prevWeekBytes/growthBytes/growthRatePct 为 null
     */
    public List<TableGrowthRow> queryTopN(Long instanceId, String metricCode, int limit) {
        int effectiveLimit = Math.max(1, Math.min(200, limit));
        List<Map<String, Object>> rows = mapper.selectTopN(instanceId, metricCode, effectiveLimit);
        List<TableGrowthRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Number currentBytes = (Number) row.get("current_bytes");
            Number prevWeekBytes = (Number) row.get("prev_week_bytes");
            Number growthBytes = (Number) row.get("growth_bytes");
            Number growthRatePct = (Number) row.get("growth_rate_pct");
            result.add(new TableGrowthRow(
                    (String) row.get("object_name"),
                    (String) row.get("object_type"),
                    currentBytes != null ? currentBytes.longValue() : 0L,
                    prevWeekBytes != null ? prevWeekBytes.longValue() : null,
                    growthBytes != null ? growthBytes.longValue() : null,
                    growthRatePct != null ? growthRatePct.doubleValue() : null
            ));
        }
        return result;
    }

    /** 单表周环比数据行。 */
    public record TableGrowthRow(
            String objectName,
            String objectType,
            long currentBytes,
            Long prevWeekBytes,
            Long growthBytes,
            Double growthRatePct
    ) {}
}
