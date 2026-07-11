package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstanceDataCleanupMapper {

    @Delete("""
            <script>
            DELETE FROM alert_notify_record
             WHERE event_id IN (SELECT id FROM alert_event WHERE instance_id = #{instanceId});
            DELETE FROM alert_event_operate_log
             WHERE event_id IN (SELECT id FROM alert_event WHERE instance_id = #{instanceId});
            DELETE FROM llm_analysis
             WHERE event_id IN (SELECT id FROM alert_event WHERE instance_id = #{instanceId});
            DELETE FROM alert_event WHERE instance_id = #{instanceId};
            DELETE FROM alert_rule_instance_config WHERE instance_id = #{instanceId};
            DELETE FROM scenario_instance_config WHERE instance_id = #{instanceId};
            DELETE FROM alert_evaluate_lock WHERE instance_id = #{instanceId};
            DELETE FROM alert_evaluate_window
             WHERE split_part(dedup_key, ':', 2) = CAST(#{instanceId} AS text);
            DELETE FROM instance_collector WHERE instance_id = #{instanceId};
            DELETE FROM collect_log WHERE instance_id = #{instanceId};
            DELETE FROM counter_snapshot WHERE instance_id = #{instanceId};
            DELETE FROM slow_sql_optimize_mark WHERE instance_id = #{instanceId};
            DELETE FROM metric_data_1m WHERE instance_id = #{instanceId};
            DELETE FROM metric_data_1h WHERE instance_id = #{instanceId};
            DELETE FROM metric_data_1d WHERE instance_id = #{instanceId};
            DELETE FROM metric_text_data_1m WHERE instance_id = #{instanceId};
            DELETE FROM metric_text_data_1h WHERE instance_id = #{instanceId};
            DELETE FROM metric_text_data_1d WHERE instance_id = #{instanceId};
            DELETE FROM metric_top_sql WHERE instance_id = #{instanceId};
            DELETE FROM metric_capacity_object WHERE instance_id = #{instanceId};
            DELETE FROM metric_long_conn WHERE instance_id = #{instanceId};
            DELETE FROM metric_slow_sql_sample WHERE instance_id = #{instanceId};
            </script>
            """)
    int deleteByInstanceId(@Param("instanceId") Long instanceId);
}
