package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsSlowSqlSampleWriter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsSlowSqlSampleWriterMapper {

    @Insert({
            "<script>",
            "INSERT INTO metric_slow_sql_sample",
            "(instance_id, thread_id, event_id, conn_user, conn_host, schema_name, digest, sql_text,",
            " exec_time_us, lock_time_us, rows_examined, rows_sent, sort_rows,",
            " no_index_used, tmp_tables, tmp_disk_tables, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{instanceId}, #{it.threadId}, #{it.eventId}, #{it.connUser,jdbcType=VARCHAR},",
            " #{it.connHost,jdbcType=VARCHAR}, #{it.schemaName,jdbcType=VARCHAR},",
            " #{it.digest,jdbcType=VARCHAR}, #{it.sqlText,jdbcType=VARCHAR},",
            " #{it.execTimeUs}, #{it.lockTimeUs}, #{it.rowsExamined}, #{it.rowsSent}, #{it.sortRows},",
            " #{it.noIndexUsed}, #{it.tmpTables}, #{it.tmpDiskTables},",
            " to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("instanceId") Long instanceId,
                    @Param("items") List<TsSlowSqlSampleWriter.TsSlowSqlSamplePoint> items);
}
