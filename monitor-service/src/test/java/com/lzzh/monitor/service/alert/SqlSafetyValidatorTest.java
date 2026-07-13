package com.lzzh.monitor.service.alert;

import com.lzzh.monitor.common.enums.DbType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** {@link SqlSafetyValidator} 单元测试：自定义告警 SQL 的最终安全防线，需要重点覆盖。 */
class SqlSafetyValidatorTest {

    private static void validateMysql(String sql) {
        SqlSafetyValidator.validateQueryOnly(sql, DbType.MYSQL);
    }

    @Test
    void allowsPlainSelect() {
        assertThatNoException().isThrownBy(() ->
                validateMysql("SELECT count(*) FROM information_schema.processlist"));
    }

    @Test
    void allowsWithCte() {
        assertThatNoException().isThrownBy(() ->
                validateMysql(
                        "WITH t AS (SELECT 1 AS v) SELECT v FROM t"));
    }

    @Test
    void allowsTrailingSemicolonAndWhitespace() {
        assertThatNoException().isThrownBy(() ->
                validateMysql("  SELECT 1  ;  "));
    }

    @Test
    void allowsCaseInsensitiveSelectKeyword() {
        assertThatNoException().isThrownBy(() -> validateMysql("select 1"));
    }

    @Test
    void rejectsBlankSql() {
        assertThatThrownBy(() -> validateMysql(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validateMysql("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validateMysql(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMultipleStatementsSeparatedBySemicolon() {
        assertThatThrownBy(() ->
                validateMysql("SELECT 1; DROP TABLE foo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonSelectStatements() {
        assertThatThrownBy(() -> validateMysql("DELETE FROM alert_event"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validateMysql("UPDATE alert_event SET status='closed'"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validateMysql("INSERT INTO alert_event(id) VALUES (1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validateMysql("DROP TABLE alert_event"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSyntaxError() {
        assertThatThrownBy(() -> validateMysql("SELECT FROM WHERE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM t INTO OUTFILE '/tmp/x.csv'",
            "SELECT load_file('/etc/passwd')",
            "SELECT sleep(5)",
            "SELECT benchmark(1000000, MD5('x'))",
            "SELECT pg_sleep(5)",
            "SELECT pg_read_file('/etc/passwd')",
            "SELECT * FROM t FOR UPDATE",
            "SELECT * FROM t FOR SHARE",
            "SELECT * FROM t LOCK IN SHARE MODE",
            "WAITFOR DELAY '0:0:5'",
    })
    void rejectsDangerousQueryFeatures(String sql) {
        assertThatThrownBy(() -> validateMysql(sql))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesDialectAgainstAuthoritativeDatabaseType() {
        assertThatNoException().isThrownBy(() ->
                SqlSafetyValidator.validateQueryOnly("SELECT * FROM users WHERE name ILIKE 'a%'", DbType.POSTGRESQL));
        assertThatThrownBy(() ->
                SqlSafetyValidator.validateQueryOnly("SELECT * FROM users WHERE name ILIKE 'a%'", DbType.MYSQL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PostgreSQL 专属语法");
        assertThatThrownBy(() ->
                SqlSafetyValidator.validateQueryOnly("SELECT * FROM a STRAIGHT_JOIN b ON a.id = b.id", DbType.POSTGRESQL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MySQL 专属语法");
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly("SELECT 1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数据库类型未识别");
    }

    @Test
    void dangerousFeatureCheckIsCaseInsensitive() {
        assertThatThrownBy(() -> validateMysql("SELECT SLEEP(1)"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
