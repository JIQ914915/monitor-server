package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.CollectLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 采集日志 Mapper。 */
@Mapper
public interface CollectLogMapper extends BaseMapper<CollectLog> {

    /**
     * 查询各实例各频率的最近一条采集记录（用于任务列表页展示最新状态）。
     * 通过 DISTINCT ON (instance_id, frequency) + ORDER BY collect_time DESC 取最新行。
     */
    List<Map<String, Object>> selectLatestPerTask();

    /**
     * 查询单实例单频率的近期日志（倒序）。
     *
     * @param instanceId 实例 ID
     * @param frequency  频率（1m/1h/1d）
     * @param limit      最多返回条数
     */
    List<CollectLog> selectRecent(
            @Param("instanceId") Long instanceId,
            @Param("frequency") String frequency,
            @Param("limit") int limit);
}
