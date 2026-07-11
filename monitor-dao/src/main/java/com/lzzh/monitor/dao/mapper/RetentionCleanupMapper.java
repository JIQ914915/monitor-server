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
}
