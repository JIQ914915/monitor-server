package com.lzzh.monitor.collector.sqlserver.item;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SqlServerDiagnosticSafetyTest {
    @Test void redactsSqlLiterals() {
        assertThat(SqlServerSqlRedactor.redact("select * from patient where name=N'张三' and id=42 and token=0xCAFE"))
                .isEqualTo("select * from patient where name=? and id=? and token=?");
    }

    @Test void categorizesRawWaitTypes() {
        assertThat(SqlServerWaitStatsItem.categoryOf("LCK_M_X")).isEqualTo("lock");
        assertThat(SqlServerWaitStatsItem.categoryOf("PAGEIOLATCH_SH")).isEqualTo("io");
        assertThat(SqlServerWaitStatsItem.categoryOf("HADR_SYNC_COMMIT")).isEqualTo("ha");
        assertThat(SqlServerWaitStatsItem.categoryOf("UNKNOWN_WAIT")).isEqualTo("other");
    }
    @Test void keepsDeadlockXmlButRedactsInputBuffer() {
        String xml="<event><data><inputbuf>update t set secret='abc' where id=9</inputbuf></data></event>";
        assertThat(SqlServerDeadlockItem.redactAndLimit(xml))
                .contains("<event>","secret=?","id=?").doesNotContain("abc");
    }
}
