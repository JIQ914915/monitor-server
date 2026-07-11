package com.lzzh.monitor.api.response;

/**
 * 配置参数分页条目 VO。
 * <p>合并 mysql.var.* / mysql.var_text.* 当前值与 mysql_param_meta 元数据，
 * 用于实时概况「配置 Tab」服务端分页接口。
 */
public class ParamPageItemVo {

    // ── 当前值（来自指标 DB） ─────────────────────────────────────────────

    /** 参数名（如 max_connections）。 */
    private String paramName;

    /** 指标编码（如 mysql.var.max_connections）。 */
    private String metricCode;

    /** 值类型：numeric 或 text。 */
    private String valueType;

    /** 当前值（统一字符串）；无数据时为 null。 */
    private String value;

    /** 是否有当前采集值。 */
    private boolean hasValue;

    // ── 元数据（来自 mysql_param_meta，无记录则为 null） ─────────────────

    /** 友好中文名。 */
    private String displayName;

    /** 分类（connection / innodb / logging / security / general）。 */
    private String category;

    /** 是否动态可在线修改。 */
    private Boolean isDynamic;

    /** 值单位（bytes / seconds / count / bool / 空字符串）。 */
    private String unit;

    /** 参数说明。 */
    private String description;

    // ── Getters / Setters ────────────────────────────────────────────────

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

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Boolean getIsDynamic() { return isDynamic; }
    public void setIsDynamic(Boolean isDynamic) { this.isDynamic = isDynamic; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
