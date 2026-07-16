package com.lzzh.monitor.collector.sqlserver.item;

import java.util.regex.Pattern;

/** 采集侧 SQL 脱敏：隐藏字符串、十六进制和数值字面量，不记录连接凭据。 */
final class SqlServerSqlRedactor {
    private static final Pattern STRING = Pattern.compile("N?'(?:''|[^'])*'");
    private static final Pattern HEX = Pattern.compile("0x[0-9a-fA-F]+");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w])[-+]?\\d+(?:\\.\\d+)?(?![\\w])");

    private SqlServerSqlRedactor() {}

    static String redact(String sql) {
        if (sql == null) return "";
        String result = STRING.matcher(sql).replaceAll("?");
        result = HEX.matcher(result).replaceAll("?");
        result = NUMBER.matcher(result).replaceAll("?");
        result = result.replaceAll("\\s+", " ").trim();
        return result.length() <= 2000 ? result : result.substring(0, 2000);
    }
}
