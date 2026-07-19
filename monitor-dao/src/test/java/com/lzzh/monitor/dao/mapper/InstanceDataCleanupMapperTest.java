package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceDataCleanupMapperTest {

    private static final String CLEANUP_NAMESPACE = InstanceDataCleanupMapper.class.getName();
    private static final String ALERT_WINDOW_NAMESPACE = AlertEvaluateWindowMapper.class.getName();

    @Test
    void mapperXmlBuildsBoundSqlForCleanupAndAlertWindowStatements() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        parseMapper(configuration, "mapper/InstanceDataCleanupMapper.xml");
        parseMapper(configuration, "mapper/AlertEvaluateWindowMapper.xml");

        BoundSql cleanup = boundSql(configuration, CLEANUP_NAMESPACE + ".deleteByInstanceId",
                Map.of("instanceId", 1L));
        assertThat(cleanup.getSql().toLowerCase()).contains(
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
                "delete from metric_slow_sql_sample",
                "delete from pg_operational_event",
                "delete from pg_operational_snapshot",
                "delete from pg_collect_item_status");

        BoundSql selectWindow = boundSql(configuration, ALERT_WINDOW_NAMESPACE + ".selectWindow",
                Map.of("dedupKey", "rule:1", "windowType", "trigger"));
        assertThat(selectWindow.getSql()).contains("FROM alert_evaluate_window", "dedup_key = ?", "window_type = ?");
        assertThat(selectWindow.getParameterMappings()).hasSize(2);

        OffsetDateTime now = OffsetDateTime.now();
        BoundSql touch = boundSql(configuration, ALERT_WINDOW_NAMESPACE + ".touch",
                Map.of("dedupKey", "rule:1", "windowType", "trigger", "now", now,
                        "expireTime", now.plusMinutes(5)));
        assertThat(touch.getSql()).contains("INSERT INTO alert_evaluate_window", "ON CONFLICT");
        assertThat(touch.getParameterMappings()).hasSize(5);

        BoundSql deleteByDedupKeys = boundSql(configuration, ALERT_WINDOW_NAMESPACE + ".deleteByDedupKeys",
                Map.of("keys", List.of("rule:1", "rule:2")));
        assertThat(deleteByDedupKeys.getSql().replaceAll("\\s+", ""))
                .contains("DELETEFROMalert_evaluate_window", "IN(?,?)");
        assertThat(deleteByDedupKeys.getParameterMappings()).hasSize(2);
    }

    private static void parseMapper(MybatisConfiguration configuration, String resourcePath) throws Exception {
        try (InputStream resource = InstanceDataCleanupMapperTest.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertThat(resource).as(resourcePath).isNotNull();
            new XMLMapperBuilder(resource, configuration, resourcePath, configuration.getSqlFragments()).parse();
        }
    }

    private static BoundSql boundSql(MybatisConfiguration configuration, String statementId,
                                     Map<String, ?> parameters) {
        assertThat(configuration.hasStatement(statementId, false)).as(statementId).isTrue();
        return configuration.getMappedStatement(statementId, false).getBoundSql(parameters);
    }
}
