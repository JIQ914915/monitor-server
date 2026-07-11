package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.CollectLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 采集日志 Mapper。 */
@Mapper
public interface CollectLogMapper extends BaseMapper<CollectLog> {

    /**
     * 查询各实例各频率的最近一条采集记录（用于任务列表页展示最新状态）。
     * 通过 DISTINCT ON (instance_id, frequency) + ORDER BY collect_time DESC 取最新行。
     */
    @Select("""
            SELECT DISTINCT ON (l.instance_id, l.frequency)
                l.instance_id,
                l.frequency,
                l.collect_time   AS last_collect_time,
                l.duration_ms    AS last_duration_ms,
                l.metric_count   AS last_metric_count,
                l.success        AS last_success,
                l.error_message  AS last_error_message,
                (SELECT COUNT(*) FROM collect_log s
                 WHERE s.instance_id = l.instance_id AND s.frequency = l.frequency
                   AND s.collect_time >= now() - INTERVAL '24 hours') AS total_24h,
                (SELECT COUNT(*) FROM collect_log s
                 WHERE s.instance_id = l.instance_id AND s.frequency = l.frequency
                   AND s.collect_time >= now() - INTERVAL '24 hours'
                   AND s.success = TRUE) AS success_24h
            FROM collect_log l
            ORDER BY l.instance_id, l.frequency, l.collect_time DESC
            """)
    List<Map<String, Object>> selectLatestPerTask();

    /**
     * 查询单实例单频率的近期日志（倒序）。
     *
     * @param instanceId 实例 ID
     * @param frequency  频率（1m/1h/1d）
     * @param limit      最多返回条数
     */
    @Select("""
            SELECT id, instance_id, frequency, collect_time, duration_ms,
                   metric_count, text_count, object_count, success, error_message
            FROM collect_log
            WHERE instance_id = #{instanceId} AND frequency = #{frequency}
            ORDER BY collect_time DESC
            LIMIT #{limit}
            """)
    List<CollectLog> selectRecent(
            @Param("instanceId") Long instanceId,
            @Param("frequency") String frequency,
            @Param("limit") int limit);
}
