package com.lzzh.monitor.api.response;

import java.util.Map;

/**
 * 多指标最新值批量查询响应 VO。
 * <p>key 为指标编码，value 为最新值（null 表示该指标在新鲜窗口内无数据）。
 */
public class MetricLatestVo {

    /** 实例 ID。 */
    private Long instanceId;

    /**
     * 指标最新值 Map。
     * <p>key：指标编码（如 mysql.qps）；value：最新值（null 表示无新鲜数据）。
     */
    private Map<String, Double> values;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public Map<String, Double> getValues() { return values; }
    public void setValues(Map<String, Double> values) { this.values = values; }
}
