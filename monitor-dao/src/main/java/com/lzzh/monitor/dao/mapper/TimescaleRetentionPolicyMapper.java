package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TimescaleRetentionPolicyMapper {

    @Select("SELECT COUNT(*) FROM pg_extension WHERE extname = 'timescaledb'")
    Integer countTimescaleExtensions();

    @Select("SELECT COUNT(*) FROM timescaledb_information.hypertables WHERE hypertable_name = #{table}")
    Integer countHypertablesByName(@Param("table") String table);

    @Select("SELECT COUNT(*) FROM timescaledb_information.continuous_aggregates WHERE view_name = #{view}")
    Integer countContinuousAggregatesByName(@Param("view") String view);

    @Select("SELECT remove_retention_policy(CAST(#{table} AS regclass), if_exists => true)")
    Object removeRetentionPolicy(@Param("table") String table);

    @Select("SELECT add_retention_policy(CAST(#{table} AS regclass), "
            + "make_interval(days => #{retentionDays}), if_not_exists => true)")
    Object addRetentionPolicy(@Param("table") String table,
                              @Param("retentionDays") int retentionDays);
}
