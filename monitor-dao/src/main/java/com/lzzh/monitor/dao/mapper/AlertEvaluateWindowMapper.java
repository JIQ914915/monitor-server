package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.entity.AlertEvaluateWindow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/** 告警持续窗口状态 Mapper。 */
@Mapper
public interface AlertEvaluateWindowMapper {

    @Select("""
            SELECT dedup_key, window_type, first_match_time, last_eval_time, expire_time, created_at, updated_at
            FROM alert_evaluate_window
            WHERE dedup_key = #{dedupKey}
              AND window_type = #{windowType}
            """)
    AlertEvaluateWindow selectWindow(@Param("dedupKey") String dedupKey,
                                     @Param("windowType") String windowType);

    /** 全量加载窗口状态（表内仅存活跃 pending 窗口，行数很小），供评估轮次预载。 */
    @Select("""
            SELECT dedup_key, window_type, first_match_time, last_eval_time, expire_time, created_at, updated_at
            FROM alert_evaluate_window
            """)
    List<AlertEvaluateWindow> selectAll();

    /**
     * 推进持续窗口：不存在则新建（first_match_time = now），已存在则仅刷新 last_eval_time/expire_time，
     * 保留 first_match_time 作为持续时长起点。
     * <p>特例：命中的行已过期（expire_time 已过但清理任务尚未删除）时，视同新窗口重置
     * first_match_time，避免"复活"陈旧起点导致持续时长判定被立即满足（绕过 duration）。
     */
    @Insert("""
            INSERT INTO alert_evaluate_window (
                dedup_key, window_type, first_match_time, last_eval_time, expire_time
            )
            VALUES (
                #{dedupKey}, #{windowType}, #{now}, #{now}, #{expireTime}
            )
            ON CONFLICT (dedup_key, window_type) DO UPDATE
            SET first_match_time = CASE
                    WHEN alert_evaluate_window.expire_time IS NOT NULL
                         AND alert_evaluate_window.expire_time < now()
                    THEN EXCLUDED.first_match_time
                    ELSE alert_evaluate_window.first_match_time
                END,
                last_eval_time = EXCLUDED.last_eval_time,
                expire_time = EXCLUDED.expire_time,
                updated_at = now()
            """)
    int touch(@Param("dedupKey") String dedupKey,
              @Param("windowType") String windowType,
              @Param("now") OffsetDateTime now,
              @Param("expireTime") OffsetDateTime expireTime);

    @Delete("""
            DELETE FROM alert_evaluate_window
            WHERE dedup_key = #{dedupKey}
              AND window_type = #{windowType}
            """)
    int deleteWindow(@Param("dedupKey") String dedupKey,
                     @Param("windowType") String windowType);

    @Delete("""
            DELETE FROM alert_evaluate_window
            WHERE expire_time IS NOT NULL
              AND expire_time < now()
            """)
    int deleteExpired();

    /**
     * 按 dedup_key 批量删除窗口（trigger/recovery 两种类型一并删除）。
     * 供人工静默/关闭事件时清理持续窗口，避免解除处置后旧窗口使新触发绕过持续时长判定。
     */
    @Delete("""
            <script>
            DELETE FROM alert_evaluate_window
            WHERE dedup_key IN
            <foreach item="key" collection="keys" open="(" separator="," close=")">#{key}</foreach>
            </script>
            """)
    int deleteByDedupKeys(@Param("keys") List<String> keys);

    /**
     * 按"精确 dedup_key + 维度前缀"删除窗口，供规则停用/删除联动清理。
     * 覆盖尚未生成事件的 pending 窗口（此时事件表中无记录，只能按规则维度清）。
     */
    @Delete("""
            DELETE FROM alert_evaluate_window
            WHERE dedup_key = #{exactKey}
               OR dedup_key LIKE #{prefix} || '%'
            """)
    int deleteByRuleInstance(@Param("exactKey") String exactKey, @Param("prefix") String prefix);
}
