package com.lzzh.monitor.dao.retention;

import com.lzzh.monitor.dao.mapper.RetentionCleanupMapper;
import org.springframework.stereotype.Repository;

/**
 * 普通表保留清理 DAO。
 */
@Repository
public class RetentionCleanupDao {

    private final RetentionCleanupMapper mapper;

    public RetentionCleanupDao(RetentionCleanupMapper mapper) {
        this.mapper = mapper;
    }

    public int deleteAlertEventsOlderThanDays(int retentionDays) {
        return mapper.deleteAlertEventsOlderThanDays(retentionDays);
    }

    public int deleteOperLogsOlderThanDays(int retentionDays) {
        return mapper.deleteOperLogsOlderThanDays(retentionDays);
    }
    public int deletePgOperationalEventsOlderThanDays(int retentionDays) {
        return mapper.deletePgOperationalEventsOlderThanDays(retentionDays);
    }
}
