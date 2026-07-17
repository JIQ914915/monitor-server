package com.lzzh.monitor.common.datatype;

import java.util.Locale;

/** 数据库类型稳定码值规范：持久化、缓存和服务端判断统一使用大写 code。 */
public final class DatabaseTypeCode {

    private DatabaseTypeCode() {
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean equals(String left, String right) {
        String normalizedLeft = normalize(left);
        return normalizedLeft != null && normalizedLeft.equals(normalize(right));
    }
}
