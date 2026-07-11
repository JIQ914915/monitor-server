package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsParamQueryMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置参数查询 DAO。
 * <p>从 {@code metric_data_1d}（数值型参数，mysql.var.*）和
 * {@code metric_text_data_1d}（文本型参数，mysql.var_text.*）查询最新值，
 * 用于实时概况「配置 Tab」当前值列。
 * <p>使用 {@code DISTINCT ON (metric_code)} 高效取各参数最新快照；
 * 新鲜窗口 2 天（天级采集，容忍 1 天延迟）。
 */
@Repository
public class TsParamQueryDao {

    private final TsParamQueryMapper mapper;

    public TsParamQueryDao(TsParamQueryMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 批量查询数值型参数最新值（metric_data_1d）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码集合（如 mysql.var.max_connections）
     * @return Map(metricCode → 最新值)；不在 Map 中表示无新鲜数据
     */
    public Map<String, Double> latestNumericParams(Long instanceId, List<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = mapper.selectLatestNumeric(instanceId, metricCodes);
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

    /**
     * 批量查询文本型参数最新值（metric_text_data_1d）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码集合（如 mysql.var_text.sql_mode）
     * @return Map(metricCode → 最新文本值)；不在 Map 中表示无新鲜数据
     */
    public Map<String, String> latestTextParams(Long instanceId, List<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = mapper.selectLatestText(instanceId, metricCodes);
        Map<String, String> result = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("metric_code");
            String text = (String) row.get("value_text");
            if (code != null) {
                result.put(code, text);
            }
        }
        return result;
    }
}
