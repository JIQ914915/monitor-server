package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.entity.AlertEvaluateWindow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** 告警持续窗口状态 Mapper。 */
@Mapper
public interface AlertEvaluateWindowMapper {

    AlertEvaluateWindow selectWindow(@Param("dedupKey") String dedupKey,
                                     @Param("windowType") String windowType);

    /** 全量加载窗口状态（表内仅存活跃 pending 窗口，行数很小），供评估轮次预载。 */
    List<AlertEvaluateWindow> selectAll();

    /**
     * 推进持续窗口：不存在则新建（first_match_time = now），已存在则仅刷新 last_eval_time/expire_time，
     * 保留 first_match_time 作为持续时长起点。
     * <p>特例：命中的行已过期（expire_time 已过但清理任务尚未删除）时，视同新窗口重置
     * first_match_time，避免"复活"陈旧起点导致持续时长判定被立即满足（绕过 duration）。
     */
    int touch(@Param("dedupKey") String dedupKey,
              @Param("windowType") String windowType,
              @Param("now") OffsetDateTime now,
              @Param("expireTime") OffsetDateTime expireTime);

    int deleteWindow(@Param("dedupKey") String dedupKey,
                     @Param("windowType") String windowType);

    int deleteExpired();

    /**
     * 按 dedup_key 批量删除窗口（trigger/recovery 两种类型一并删除）。
     * 供人工静默/关闭事件时清理持续窗口，避免解除处置后旧窗口使新触发绕过持续时长判定。
     */
    int deleteByDedupKeys(@Param("keys") List<String> keys);

    /**
     * 按"精确 dedup_key + 维度前缀"删除窗口，供规则停用/删除联动清理。
     * 覆盖尚未生成事件的 pending 窗口（此时事件表中无记录，只能按规则维度清）。
     */
    int deleteByRuleInstance(@Param("exactKey") String exactKey, @Param("prefix") String prefix);
}
