package com.lzzh.monitor.common.enums;

/** 数据库类型注册表。新增类型在此登记，并新增对应 monitor-collector-&lt;db&gt; 模块实现。 */
public enum DbType {

    MYSQL("MySQL"),
    ORACLE("Oracle"),
    POSTGRESQL("PostgreSQL"),
    SQLSERVER("SQLServer");

    private final String label;

    DbType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static DbType of(String name) {
        for (DbType t : values()) {
            if (t.name().equalsIgnoreCase(name) || t.label.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}
