package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RetentionCleanupMapper {

    @Delete("DELETE FROM alert_event WHERE created_at < now() - make_interval(days => #{retentionDays})")
    int deleteAlertEventsOlderThanDays(@Param("retentionDays") int retentionDays);

    @Delete("DELETE FROM sys_oper_log WHERE oper_time < now() - make_interval(days => #{retentionDays})")
    int deleteOperLogsOlderThanDays(@Param("retentionDays") int retentionDays);

    @Delete("DELETE FROM pg_operational_event WHERE event_time < now() - make_interval(days => #{retentionDays})")
    int deletePgOperationalEventsOlderThanDays(@Param("retentionDays") int retentionDays);

    @Delete("DELETE FROM mysql_plan_history WHERE captured_at < now() - make_interval(days => #{retentionDays})")
    int deleteMySqlPlanHistoryOlderThanDays(@Param("retentionDays") int retentionDays);

    @Delete("DELETE FROM mysql_security_snapshot WHERE captured_at < now() - make_interval(days => #{retentionDays})")
    int deleteMySqlSecuritySnapshotsOlderThanDays(@Param("retentionDays") int retentionDays);
}
