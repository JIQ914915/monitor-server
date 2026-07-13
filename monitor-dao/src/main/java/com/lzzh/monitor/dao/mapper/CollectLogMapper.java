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

    /** 报告中心内部使用：查询全部任务的最新采集质量，不用于页面列表。 */
    List<Map<String, Object>> selectLatestPerTask();
    /** 按查询条件分页获取数据库实例/主机采集任务。 */
    List<Map<String, Object>> selectTaskPage(
            @Param("keyword") String keyword,
            @Param("dbType") String dbType,
            @Param("frequency") String frequency,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 统计符合条件的采集任务总数。 */
    long countTasks(
            @Param("keyword") String keyword,
            @Param("dbType") String dbType,
            @Param("frequency") String frequency,
            @Param("status") String status);

    /** 统计符合条件的任务频率及状态分布。 */
    Map<String, Object> selectTaskStats(
            @Param("keyword") String keyword,
            @Param("dbType") String dbType,
            @Param("frequency") String frequency,
            @Param("status") String status);

    /** 按实例或主机分页查询采集历史日志。 */
    List<CollectLog> selectRecent(
            @Param("instanceId") Long instanceId,
            @Param("hostId") Long hostId,
            @Param("frequency") String frequency,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 统计实例或主机的采集历史日志总数。 */
    long countRecent(
            @Param("instanceId") Long instanceId,
            @Param("hostId") Long hostId,
            @Param("frequency") String frequency);
}