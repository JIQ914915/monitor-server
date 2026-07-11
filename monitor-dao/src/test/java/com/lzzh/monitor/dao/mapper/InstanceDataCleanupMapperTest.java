package com.lzzh.monitor.dao.mapper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceDataCleanupMapperTest {

    @Test
    void cleanupSqlCoversAllInstanceOwnedTables() throws Exception {
        try (InputStream resource = InstanceDataCleanupMapperTest.class.getClassLoader()
                .getResourceAsStream("mapper/InstanceDataCleanupMapper.xml")) {
            assertThat(resource).isNotNull();
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql).contains(
                "delete from alert_notify_record",
                "delete from alert_event_operate_log",
                "delete from llm_analysis",
                "delete from alert_event",
                "delete from alert_rule_instance_config",
                "delete from scenario_instance_config",
                "delete from alert_evaluate_lock",
                "delete from alert_evaluate_window",
                "delete from instance_collector",
                "delete from collect_log",
                "delete from counter_snapshot",
                "delete from slow_sql_optimize_mark",
                "delete from metric_data_1m",
                "delete from metric_data_1h",
                "delete from metric_data_1d",
                "delete from metric_text_data_1m",
                "delete from metric_text_data_1h",
                "delete from metric_text_data_1d",
                "delete from metric_top_sql",
                "delete from metric_capacity_object",
                "delete from metric_long_conn",
                "delete from metric_slow_sql_sample");
        }
    }
}
