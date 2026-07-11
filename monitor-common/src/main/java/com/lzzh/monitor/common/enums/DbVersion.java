package com.lzzh.monitor.common.enums;

/** 数据库版本注册表（首期仅 MySQL 5.6/5.7/8.0）。新增版本在此追加。 */
public enum DbVersion {

    MYSQL_5_6(DbType.MYSQL, "5.6"),
    MYSQL_5_7(DbType.MYSQL, "5.7"),
    MYSQL_8_0(DbType.MYSQL, "8.0");
    // 后续：ORACLE_11G / PG_14 ...

    private final DbType dbType;
    private final String version;

    DbVersion(DbType dbType, String version) {
        this.dbType = dbType;
        this.version = version;
    }

    public DbType dbType() {
        return dbType;
    }

    public String version() {
        return version;
    }
}
