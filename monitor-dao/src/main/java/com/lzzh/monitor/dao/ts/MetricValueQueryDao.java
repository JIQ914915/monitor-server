package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.MetricValueQueryMapper;
import org.springframework.stereotype.Repository;

/**
 * 指标最新值查询 DAO。
 */
@Repository
public class MetricValueQueryDao {

    private final MetricValueQueryMapper mapper;

    public MetricValueQueryDao(MetricValueQueryMapper mapper) {
        this.mapper = mapper;
    }

    public Double latest1m(Long instanceId, String metricCode, int windowMinutes) {
        return mapper.selectLatest1m(instanceId, metricCode, windowMinutes);
    }

    public Double latest1h(Long instanceId, String metricCode, int windowHours) {
        return mapper.selectLatest1h(instanceId, metricCode, windowHours);
    }

    public Double latest1d(Long instanceId, String metricCode, int windowDays) {
        return mapper.selectLatest1d(instanceId, metricCode, windowDays);
    }

    /**
     * 环比基准点取数：按指标频率取 {@code offsetMinutes} 分钟前附近的历史值。
     * <p>容差窗口按频率放大：1m 取 ±5 分钟内最新点，1h 取 ±90 分钟，1d 取 ±36 小时，
     * 保证降采样/延迟场景仍能取到基准点；取不到返回 {@code null}（调用方按数据缺失处理）。
     */
    public Double valueAtOffset(Long instanceId, String metricCode, String frequency, int offsetMinutes) {
        String normalized = frequency == null ? "1m" : frequency.trim().toLowerCase();
        return switch (normalized) {
            case "1h" -> mapper.selectValueAt1h(instanceId, metricCode, offsetMinutes, 90);
            case "1d" -> mapper.selectValueAt1d(instanceId, metricCode, offsetMinutes, 36 * 60);
            default -> mapper.selectValueAt1m(instanceId, metricCode, offsetMinutes, 5);
        };
    }
}
