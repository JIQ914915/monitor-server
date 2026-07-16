package com.lzzh.monitor.service.postgresql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgOperationalGuidanceTest {
    @Test
    void shouldExplainWalArchiveFailureWithEvidencePath() {
        PgOperationalGuidance guidance=PgOperationalGuidance.resolve("backup","wal_archiver","warning");
        assertThat(guidance.conclusion()).contains("WAL").contains("失败");
        assertThat(guidance.action()).contains("失败次数").contains("最近成功时间");
    }

    @Test
    void shouldNotTreatUnavailableCollectionAsHealthy() {
        PgOperationalGuidance guidance=PgOperationalGuidance.resolve("replication","wal_receiver_unavailable","warning");
        assertThat(guidance.conclusion()).contains("暂不可用");
        assertThat(guidance.impact()).contains("不能据此认定为正常");
    }
}
