package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsTextReaderMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本指标读取 DAO。
 * <p>从 {@code metric_text_data_1m}（分钟级）和 {@code metric_text_data_1d}（天级）
 * 查询文本指标的最新值，支持批量和趋势两种模式：
 * <ul>
 *   <li><b>最新值</b>：DISTINCT ON 取最新一条，用于配置 Tab 当前值、复制状态等</li>
 *   <li><b>变更历史</b>：按 collect_time 排序取最近 N 条，用于参数变更审计</li>
 * </ul>
 */
@Repository
public class TsTextReader {

    /** 单次变更历史最大返回条数。 */
    private static final int MAX_HISTORY = 100;

    private final TsTextReaderMapper mapper;

    public TsTextReader(TsTextReaderMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 批量查询 1m 表中文本指标的最新值（复制状态/错误日志等分钟级文本）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码集合
     * @return Map(metricCode → 最新文本值)
     */
    public Map<String, String> latestFrom1m(Long instanceId, Collection<String> metricCodes) {
        return queryLatestText(true, instanceId, metricCodes);
    }

    /**
     * 批量查询 1h 表中文本指标的最新值（膨胀 Top 表等小时级文本，2 小时新鲜窗口）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码集合
     * @return Map(metricCode → 最新文本值)
     */
    public Map<String, String> latestFrom1h(Long instanceId, Collection<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = mapper.selectLatestFrom1h(instanceId, metricCodes);
        Map<String, String> result = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("metric_code");
            if (code != null) {
                result.put(code, (String) row.get("value_text"));
            }
        }
        return result;
    }

    /**
     * 批量查询 1d 表中文本指标的最新值（配置参数天级文本）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码集合
     * @return Map(metricCode → 最新文本值)
     */
    public Map<String, String> latestFrom1d(Long instanceId, Collection<String> metricCodes) {
        return queryLatestText(false, instanceId, metricCodes);
    }

    /**
     * 查询单指标变更历史（仅 1d 表，最多 {@value MAX_HISTORY} 条，按时间降序）。
     *
     * @param instanceId 实例 ID
     * @param metricCode 指标编码
     * @return 变更历史列表（时间降序）
     */
    public List<TextHistoryRow> historyFrom1d(Long instanceId, String metricCode) {
        List<Map<String, Object>> rows = mapper.selectHistoryFrom1d(instanceId, metricCode);
        List<TextHistoryRow> result = new ArrayList<>(Math.min(rows.size(), MAX_HISTORY));
        for (Map<String, Object> row : rows) {
            Timestamp ts = (Timestamp) row.get("collect_time");
            result.add(new TextHistoryRow(
                    (String) row.get("metric_code"),
                    (String) row.get("value_text"),
                    ts != null ? ts.toInstant().toEpochMilli() : 0L
            ));
        }
        return result;
    }

    /**
     * 查询单指标 1m 表时间区间内的全部文本点（按时间升序，最多 2000 条）。
     * <p>用于把逐分钟变化的明细类文本指标（如 host.diskio.detail）还原为趋势序列。
     *
     * @param instanceId 实例 ID
     * @param metricCode 指标编码
     * @param fromMs     起始时间（毫秒时间戳）
     * @param toMs       结束时间（毫秒时间戳）
     */
    public List<TextHistoryRow> rangeFrom1m(Long instanceId, String metricCode, long fromMs, long toMs) {
        List<Map<String, Object>> rows = mapper.selectRangeFrom1m(instanceId, metricCode, fromMs, toMs);
        List<TextHistoryRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Timestamp ts = (Timestamp) row.get("collect_time");
            result.add(new TextHistoryRow(
                    (String) row.get("metric_code"),
                    (String) row.get("value_text"),
                    ts != null ? ts.toInstant().toEpochMilli() : 0L
            ));
        }
        return result;
    }

    private Map<String, String> queryLatestText(boolean minuteLevel, Long instanceId, Collection<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = minuteLevel
                ? mapper.selectLatestFrom1m(instanceId, metricCodes)
                : mapper.selectLatestFrom1d(instanceId, metricCodes);
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

    /** 文本指标历史变更行。 */
    public record TextHistoryRow(
            String metricCode,
            String valueText,
            long collectTimeMs
    ) {}
}
