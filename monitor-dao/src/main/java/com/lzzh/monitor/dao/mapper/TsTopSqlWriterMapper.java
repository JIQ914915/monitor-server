package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsTopSqlWriter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsTopSqlWriterMapper {

    @Insert({
            "<script>",
            "INSERT INTO metric_top_sql",
            "(instance_id, schema_name, digest, digest_text,",
            " count_star, sum_timer_wait, rows_examined, rows_sent,",
            " delta_count, delta_timer_wait, avg_timer_wait_us,",
            " delta_rows_examined, delta_rows_sent,",
            " delta_lock_time, delta_sort_rows, delta_no_index_used,",
            " delta_tmp_tables, delta_tmp_disk_tables, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{it.instanceId}, #{it.schemaName}, #{it.digest}, #{it.digestText},",
            " #{it.countStar}, #{it.sumTimerWait}, #{it.rowsExamined}, #{it.rowsSent},",
            " #{it.deltaCount}, #{it.deltaTimerWait}, #{it.avgTimerWaitUs},",
            " #{it.deltaRowsExamined}, #{it.deltaRowsSent},",
            " #{it.deltaLockTime}, #{it.deltaSortRows}, #{it.deltaNoIndexUsed},",
            " #{it.deltaTmpTables}, #{it.deltaTmpDiskTables}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("items") List<TsTopSqlWriter.TsTopSqlPoint> items);
}
