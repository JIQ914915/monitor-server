package com.lzzh.monitor.common.datatype;

import java.util.regex.Pattern;

/** JDBC URL 模板渲染器，正式采集和连接测试共用。 */
public final class JdbcUrlTemplate {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{[^{}]+}");

    private JdbcUrlTemplate() {
    }

    public static String render(String template, String host, Integer port, String database) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("数据库类型未配置 JDBC URL 模板");
        }
        String url = template
                .replace("{host}", value(host))
                .replace("{port}", port == null ? "" : String.valueOf(port))
                .replace("{database}", value(database));
        if (UNRESOLVED_PLACEHOLDER.matcher(url).find()) {
            throw new IllegalArgumentException("JDBC URL 模板包含未支持的占位符");
        }
        return url;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
