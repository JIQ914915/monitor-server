package com.lzzh.monitor.service.alert;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 自定义告警 SQL 安全校验。
 *
 * <p>前端校验只用于交互提示，后端保存和执行前都必须调用本校验作为最终防线。
 */
public final class SqlSafetyValidator {

    /**
     * 高风险查询特性黑名单（跨方言）：
     * <ul>
     *   <li>MySQL：INTO OUTFILE/DUMPFILE、load_file/sleep/benchmark、FOR UPDATE、LOCK IN SHARE MODE；</li>
     *   <li>PostgreSQL：pg_sleep/pg_sleep_for/pg_sleep_until、pg_read_file/pg_read_binary_file、FOR SHARE/KEY SHARE/NO KEY UPDATE；</li>
     *   <li>SQL Server：WAITFOR DELAY/TIME；Oracle：dbms_lock.sleep/dbms_session.sleep。</li>
     * </ul>
     * 注意 {@code \b} 对下划线不生效（下划线是 word 字符），pg_ 前缀函数须显式列出。
     */
    private static final Pattern DANGEROUS_QUERY_FEATURES = Pattern.compile(
            "\\binto\\s+(outfile|dumpfile)\\b"
                    + "|\\b(load_file|sleep|benchmark)\\s*\\("
                    + "|\\bpg_sleep(_for|_until)?\\s*\\("
                    + "|\\bpg_read_(binary_)?file\\s*\\("
                    + "|\\bfor\\s+(update|share|key\\s+share|no\\s+key\\s+update)\\b"
                    + "|\\block\\s+in\\s+share\\s+mode\\b"
                    + "|\\bwaitfor\\s+(delay|time)\\b"
                    + "|\\bdbms_(lock|session)\\s*\\.\\s*sleep\\b",
            Pattern.CASE_INSENSITIVE);

    private SqlSafetyValidator() {
    }

    public static void validateQueryOnly(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("自定义 SQL 不能为空");
        }
        String normalized = stripTrailingSemicolon(sql.trim());
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("自定义 SQL 不能为空");
        }
        if (DANGEROUS_QUERY_FEATURES.matcher(normalized).find()) {
            throw new IllegalArgumentException("自定义 SQL 包含高风险查询特性");
        }
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("自定义 SQL 解析失败，请检查语法", e);
        }
        if (statements == null || statements.getStatements() == null || statements.getStatements().size() != 1) {
            throw new IllegalArgumentException("自定义 SQL 仅允许单条查询语句");
        }
        Statement statement = statements.getStatements().get(0);
        if (!(statement instanceof Select)) {
            throw new IllegalArgumentException("自定义 SQL 仅允许查询类语句");
        }
        String firstToken = firstToken(normalized);
        if (!"select".equals(firstToken) && !"with".equals(firstToken)) {
            throw new IllegalArgumentException("自定义 SQL 仅允许 SELECT/WITH 查询");
        }
    }

    private static String stripTrailingSemicolon(String sql) {
        String result = sql;
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        if (result.contains(";")) {
            throw new IllegalArgumentException("自定义 SQL 不允许包含多语句分隔符");
        }
        return result;
    }

    private static String firstToken(String sql) {
        String s = sql.stripLeading();
        if (s.startsWith("/*")) {
            int end = s.indexOf("*/");
            if (end >= 0) {
                s = s.substring(end + 2).stripLeading();
            }
        }
        if (s.startsWith("--")) {
            int end = s.indexOf('\n');
            if (end >= 0) {
                s = s.substring(end + 1).stripLeading();
            }
        }
        int end = 0;
        while (end < s.length() && Character.isLetter(s.charAt(end))) {
            end++;
        }
        return end == 0 ? "" : s.substring(0, end).toLowerCase(Locale.ROOT);
    }
}
