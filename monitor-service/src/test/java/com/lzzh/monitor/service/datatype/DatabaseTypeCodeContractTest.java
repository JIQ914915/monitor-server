package com.lzzh.monitor.service.datatype;

import com.lzzh.monitor.common.datatype.DatabaseTypeCode;
import com.lzzh.monitor.common.datatype.JdbcUrlTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseTypeCodeContractTest {

    @Test
    void normalizesStableCodeWithoutUsingDisplayLabel() {
        assertThat(DatabaseTypeCode.normalize(" sqlserver ")).isEqualTo("SQLSERVER");
        assertThat(DatabaseTypeCode.equals("SQLSERVER", "sqlserver")).isTrue();
        assertThat(DatabaseTypeCode.equals("SQL Server", "SQLSERVER")).isFalse();
    }

    @Test
    void rendersConfiguredJdbcUrlTemplate() {
        assertThat(JdbcUrlTemplate.render(
                "jdbc:sqlserver://{host}:{port};databaseName={database}",
                "db.internal", 1433, "master"))
                .isEqualTo("jdbc:sqlserver://db.internal:1433;databaseName=master");
    }

    @Test
    void rejectsUnknownPlaceholder() {
        assertThatThrownBy(() -> JdbcUrlTemplate.render(
                "jdbc:test://{host}/{unknown}", "localhost", 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("占位符");
    }
}
