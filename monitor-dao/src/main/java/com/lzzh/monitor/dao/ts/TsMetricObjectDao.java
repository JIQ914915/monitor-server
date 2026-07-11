package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsMetricObjectMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对象级指标查询 DAO。
 * <p>从 {@code metric_capacity_object} 查询每个对象（如表、连接来源）的最新值，
 * 用于表空间 Top N、连接来源 Top N、表 I/O 热点分页等场景。
 * <p>使用 DISTINCT ON + 子查询的方式取每个对象最新值后再按值降序排序。
 */
@Repository
public class TsMetricObjectDao {

    private final TsMetricObjectMapper mapper;

    public TsMetricObjectDao(TsMetricObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询对象级指标 Top N（按 value 降序）。
     *
     * @param instanceId 实例 ID
     * @param metricCode 指标编码（如 capacity.total_size_bytes、conn.source.total）
     * @param limit      返回条数上限（1~200）
     * @return 最新快照按 value 降序排列的对象列表
     */
    public List<ObjectPoint> queryTopN(Long instanceId, String metricCode, int limit) {
        int effectiveLimit = Math.max(1, Math.min(200, limit));
        return mapRows(mapper.selectTopN(instanceId, metricCode, effectiveLimit));
    }

    /**
     * 统计对象级指标最新快照条数（2 小时新鲜窗口）。
     */
    public long countLatest(Long instanceId, String metricCode) {
        return mapper.countLatest(instanceId, metricCode);
    }

    /**
     * 分页查询对象级指标最新快照（按 value 降序）。
     *
     * @param offset 偏移量（从 0 开始）
     * @param limit  本页条数（1~200）
     */
    public List<ObjectPoint> queryPage(Long instanceId, String metricCode, int offset, int limit) {
        int effectiveOffset = Math.max(0, offset);
        int effectiveLimit = Math.max(1, Math.min(200, limit));
        return mapRows(mapper.selectPage(instanceId, metricCode, effectiveOffset, effectiveLimit));
    }

    /**
     * 按对象名批量取指定指标的最新值（2 小时新鲜窗口）。
     *
     * @return objectName → value；无数据时为空 Map
     */
    public Map<String, Double> queryLatestValuesByNames(Long instanceId, String metricCode,
                                                        Collection<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = mapper.selectLatestByNames(instanceId, metricCode, objectNames);
        Map<String, Double> result = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("object_name");
            Number value = (Number) row.get("value");
            if (name != null) {
                result.put(name, value != null ? value.doubleValue() : 0D);
            }
        }
        return result;
    }

    private List<ObjectPoint> mapRows(List<Map<String, Object>> rows) {
        List<ObjectPoint> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Timestamp ts = (Timestamp) row.get("collect_time");
            Number value = (Number) row.get("value");
            result.add(new ObjectPoint(
                    (String) row.get("object_name"),
                    (String) row.get("object_type"),
                    value != null ? value.doubleValue() : 0D,
                    ts != null ? ts.toInstant().toEpochMilli() : 0L
            ));
        }
        return result;
    }

    /** 对象指标单点：对象名、类型、值、采集时间。 */
    public record ObjectPoint(
            String objectName,
            String objectType,
            double value,
            long collectTimeMs
    ) {}
}
