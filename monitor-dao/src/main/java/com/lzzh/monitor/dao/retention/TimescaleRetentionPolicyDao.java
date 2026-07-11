package com.lzzh.monitor.dao.retention;

import com.lzzh.monitor.dao.mapper.TimescaleRetentionPolicyMapper;
import org.springframework.stereotype.Repository;

/**
 * TimescaleDB 保留策略 DAO。
 */
@Repository
public class TimescaleRetentionPolicyDao {

    private final TimescaleRetentionPolicyMapper mapper;

    public TimescaleRetentionPolicyDao(TimescaleRetentionPolicyMapper mapper) {
        this.mapper = mapper;
    }

    public boolean timescaleEnabled() {
        try {
            Integer c = mapper.countTimescaleExtensions();
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hypertableExists(String table) {
        try {
            Integer c = mapper.countHypertablesByName(table);
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 连续聚合（cagg）是否存在；add/remove_retention_policy 同样适用于 cagg 视图名。 */
    public boolean continuousAggregateExists(String view) {
        try {
            Integer c = mapper.countContinuousAggregatesByName(view);
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void removeRetentionPolicy(String table) {
        mapper.removeRetentionPolicy(table);
    }

    public void addRetentionPolicy(String table, int retentionDays) {
        mapper.addRetentionPolicy(table, retentionDays);
    }
}
