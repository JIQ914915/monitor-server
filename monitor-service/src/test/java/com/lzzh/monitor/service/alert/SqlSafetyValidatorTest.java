package com.lzzh.monitor.service.alert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** {@link SqlSafetyValidator} 单元测试：自定义告警 SQL 的最终安全防线，需要重点覆盖。 */
class SqlSafetyValidatorTest {

    @Test
    void allowsPlainSelect() {
        assertThatNoException().isThrownBy(() ->
                SqlSafetyValidator.validateQueryOnly("SELECT count(*) FROM information_schema.processlist"));
    }

    @Test
    void allowsWithCte() {
        assertThatNoException().isThrownBy(() ->
                SqlSafetyValidator.validateQueryOnly(
                        "WITH t AS (SELECT 1 AS v) SELECT v FROM t"));
    }

    @Test
    void allowsTrailingSemicolonAndWhitespace() {
        assertThatNoException().isThrownBy(() ->
                SqlSafetyValidator.validateQueryOnly("  SELECT 1  ;  "));
    }

    @Test
    void allowsCaseInsensitiveSelectKeyword() {
        assertThatNoException().isThrownBy(() -> SqlSafetyValidator.validateQueryOnly("select 1"));
    }

    @Test
    void rejectsBlankSql() {
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMultipleStatementsSeparatedBySemicolon() {
        assertThatThrownBy(() ->
                SqlSafetyValidator.validateQueryOnly("SELECT 1; DROP TABLE foo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonSelectStatements() {
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly("DELETE FROM alert_event"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly("UPDATE alert_event SET status='closed'"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly("INSERT INTO alert_event(id) VALUES (1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly("DROP TABLE alert_event"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSyntaxError() {
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly("SELECT FROM WHERE"))
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
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly(sql))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dangerousFeatureCheckIsCaseInsensitive() {
        assertThatThrownBy(() -> SqlSafetyValidator.validateQueryOnly("SELECT SLEEP(1)"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
