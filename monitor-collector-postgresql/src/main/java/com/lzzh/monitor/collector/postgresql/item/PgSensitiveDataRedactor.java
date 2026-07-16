package com.lzzh.monitor.collector.postgresql.item;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class PgSensitiveDataRedactor {
    private static final Pattern SECRET = Pattern.compile("(?i)(password|passwd|pwd|token|api[_-]?key|authorization|cookie)\\s*([=:]|=>)\\s*([^,;\\s]+|'[^']*'|\"[^\"]*\")");
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("(?<![A-Za-z_])\\b\\d+(?:\\.\\d+)?\\b");
    private PgSensitiveDataRedactor() {}
    static String redact(String value) {
        if (value == null) return null;
        String text = SECRET.matcher(value).replaceAll("$1$2<REDACTED>");
        text = STRING_LITERAL.matcher(text).replaceAll("'?'");
        text = NUMBER_LITERAL.matcher(text).replaceAll("?");
        return text.length() > 8000 ? text.substring(0, 8000) : text;
    }
    static String redactSecrets(String value) {
        if (value == null) return null;
        String text = SECRET.matcher(value).replaceAll("$1$2<REDACTED>");
        return text.length() > 8000 ? text.substring(0, 8000) : text;
    }
    static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { return Integer.toHexString(String.valueOf(value).hashCode()); }
    }
}