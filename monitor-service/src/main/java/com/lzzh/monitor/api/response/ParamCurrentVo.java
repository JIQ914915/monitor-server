package com.lzzh.monitor.api.response;

import java.util.List;

/**
 * 配置参数当前值响应 VO（实时概况「配置 Tab」）。
 * <p>合并数值型（mysql.var.*）和文本型（mysql.var_text.*）参数的最新值，
 * 统一以字符串展示；后端区分来源便于前端格式化（数值型可做阈值高亮）。
 */
public class ParamCurrentVo {

    /** 实例 ID。 */
    private Long instanceId;

    /** 参数列表（数值型 + 文本型合并，按 paramName 排序）。 */
    private List<ParamItem> params;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public List<ParamItem> getParams() { return params; }
    public void setParams(List<ParamItem> params) { this.params = params; }

    /** 单参数条目。 */
    public static class ParamItem {

        /** 参数名（去掉前缀，如 max_connections）。 */
        private String paramName;

        /** 指标编码（含前缀，如 mysql.var.max_connections）。 */
        private String metricCode;

        /**
         * 值类型：{@code numeric} 或 {@code text}。
         * <ul>
         *   <li>{@code numeric}：value 来自 metric_data_1d</li>
         *   <li>{@code text}：value 来自 metric_text_data_1d</li>
         * </ul>
         */
        private String valueType;

        /** 当前值（统一字符串，数值型保留原始 double 格式）。 */
        private String value;

        /** 是否有当前值（false 表示新鲜窗口内无数据或参数不适用于当前版本）。 */
        private boolean hasValue;

        public String getParamName() { return paramName; }
        public void setParamName(String paramName) { this.paramName = paramName; }
        public String getMetricCode() { return metricCode; }
        public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
        public String getValueType() { return valueType; }
        public void setValueType(String valueType) { this.valueType = valueType; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public boolean isHasValue() { return hasValue; }
        public void setHasValue(boolean hasValue) { this.hasValue = hasValue; }
    }
}
