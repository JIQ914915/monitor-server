package com.lzzh.monitor.api.response;

/** MySQL 配置参数元数据 VO（配置 Tab 说明列）。 */
public class ParamMetaVo {

    /** 参数名（小写）。 */
    private String paramName;

    /** 友好显示名（中文）。 */
    private String displayName;

    /** 分类：connection / innodb / logging / security / general。 */
    private String category;

    /** 是否动态可改（无需重启）。 */
    private Boolean isDynamic;

    /** 值单位（bytes / seconds / count / bool / 空字符串）。 */
    private String unit;

    /** 参数说明。 */
    private String description;

    /** 适用最低版本（5.6 / 5.7 / 8.0）。 */
    private String minVersion;

    /** 废弃版本（null 表示当前版本仍支持）。 */
    private String maxVersion;

    public String getParamName() { return paramName; }
    public void setParamName(String paramName) { this.paramName = paramName; }
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
    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = minVersion; }
    public String getMaxVersion() { return maxVersion; }
    public void setMaxVersion(String maxVersion) { this.maxVersion = maxVersion; }
}
