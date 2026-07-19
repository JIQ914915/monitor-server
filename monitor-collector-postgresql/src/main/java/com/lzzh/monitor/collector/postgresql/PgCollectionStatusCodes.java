package com.lzzh.monitor.collector.postgresql;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;

/** PG 采集质量状态编码；展示名称、颜色和排序由系统字典统一维护。 */
public final class PgCollectionStatusCodes {
    public static final String SUCCESS = "success";
    public static final String UNAVAILABLE = "unavailable";
    public static final String PARTIAL_FAILED = "partial_failed";
    public static final String FAILED = "failed";

    public static final String NONE = "none";
    public static final String NOT_ENABLED = "not_enabled";
    public static final String UNSUPPORTED = "unsupported";
    public static final String PERMISSION_DENIED = "permission_denied";
    public static final String TIMEOUT = "timeout";
    public static final String CONNECTION_FAILED = "connection_failed";
    public static final String COLLECTION_FAILED = "collection_failed";

    private PgCollectionStatusCodes() {}

    public static String reason(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLTimeoutException) return TIMEOUT;
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                if ("42501".equals(state)) return PERMISSION_DENIED;
                if ("42P01".equals(state) || "42703".equals(state)
                        || "42883".equals(state) || "0A000".equals(state)) return UNSUPPORTED;
                if (state != null && state.startsWith("08")) return CONNECTION_FAILED;
            }
            current = current.getCause();
        }
        return COLLECTION_FAILED;
    }

    public static String message(String reason) {
        return switch (reason) {
            case PERMISSION_DENIED -> "采集失败：账号缺少权限";
            case TIMEOUT -> "采集失败：目标库查询超时";
            case CONNECTION_FAILED -> "采集失败：目标库连接中断";
            case UNSUPPORTED -> "采集失败：当前 PostgreSQL 版本不支持该能力";
            default -> "采集失败：目标库查询执行异常";
        };
    }
}
