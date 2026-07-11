package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsMetricLatestMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标最新快照查询 DAO。
 * <p>用于健康分计算等需要"当前值"的场景。
 * 使用 PostgreSQL {@code DISTINCT ON} 高效取每个 metric_code 的最新一条记录，
 * 避免全表扫描；搭配 {@code collect_time > NOW() - INTERVAL '...'} 限制时间窗口，
 * 确保只读取新鲜数据（旧数据不参与评分）。
 */
@Repository
public class TsMetricLatestDao {

    private final TsMetricLatestMapper mapper;

    public TsMetricLatestDao(TsMetricLatestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 批量查询 metric_data_1m 中指定指标的最新值。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码集合
     * @return Map of metricCode → latest value；不在结果中表示无新鲜数据
     */
    public Map<String, Double> latestFrom1m(Long instanceId, Collection<String> metricCodes) {
        return queryLatest(instanceId, metricCodes, Freq.MINUTE);
    }

    /**
     * 批量查询 metric_data_1h 中指定指标的最新值（时间窗口 2 小时）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码集合
     * @return Map of metricCode → latest value；不在结果中表示无新鲜数据
     */
    public Map<String, Double> latestFrom1h(Long instanceId, Collection<String> metricCodes) {
        return queryLatest(instanceId, metricCodes, Freq.HOURLY);
    }

    /**
     * 批量查询 metric_data_1d 中指定指标的最新值。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码集合
     * @return Map of metricCode → latest value；不在结果中表示无新鲜数据
     */
    public Map<String, Double> latestFrom1d(Long instanceId, Collection<String> metricCodes) {
        return queryLatest(instanceId, metricCodes, Freq.DAILY);
    }

    private enum Freq { MINUTE, HOURLY, DAILY }

    private Map<String, Double> queryLatest(Long instanceId, Collection<String> metricCodes, Freq freq) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> codes = List.copyOf(metricCodes);
        List<Map<String, Object>> rows = switch (freq) {
            case MINUTE -> mapper.selectLatestFrom1m(instanceId, codes);
            case HOURLY -> mapper.selectLatestFrom1h(instanceId, codes);
            case DAILY -> mapper.selectLatestFrom1d(instanceId, codes);
        };
        Map<String, Double> result = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("metric_code");
            Number value = (Number) row.get("value");
            if (code != null && value != null) {
                result.put(code, value.doubleValue());
            }
        }
        return result;
    }
}
