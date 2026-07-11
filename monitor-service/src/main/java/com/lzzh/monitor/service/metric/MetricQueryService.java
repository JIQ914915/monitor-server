package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.request.ParamPageRequest;
import com.lzzh.monitor.api.response.CapacityForecastVo;
import com.lzzh.monitor.api.response.CapacityGrowthVo;
import com.lzzh.monitor.api.response.LongConnVo;
import com.lzzh.monitor.api.response.MetricLatestVo;
import com.lzzh.monitor.api.response.MetricObjectVo;
import com.lzzh.monitor.api.response.MetricTextVo;
import com.lzzh.monitor.api.response.MetricTrendVo;
import com.lzzh.monitor.api.response.ParamCurrentVo;
import com.lzzh.monitor.api.response.HostDiskIoTrendVo;
import com.lzzh.monitor.api.response.PerfTrendBatchVo;
import com.lzzh.monitor.api.response.ParamPageItemVo;
import com.lzzh.monitor.api.response.TableGrowthVo;
import com.lzzh.monitor.api.response.TableIoPageVo;
import com.lzzh.monitor.api.response.TodayStatsVo;
import com.lzzh.monitor.api.response.UnusedIndexPageVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 指标查询服务：查询时序指标数据（非指标元数据）。 */
public interface MetricQueryService {

    /**
     * 查询实例容量增长趋势（7 日环比，最近 N 天日级快照）。
     *
     * @param instanceId 实例 ID
     * @param days       查询天数（1~90，超出范围按边界处理），默认 30
     * @return 容量增长趋势 VO；若 capacity_instance_daily 物化视图尚未刷新，trend 为空列表
     */
    CapacityGrowthVo capacityGrowthTrend(Long instanceId, int days);

    /**
     * 容量预测：基于最近日级容量快照的线性日均增长 + 关联主机数据盘剩余空间，
     * 估算预计剩余可用天数。无法估算时返回原因说明（note）。
     *
     * @param instanceId 实例 ID
     * @return 容量预测 VO
     */
    CapacityForecastVo capacityForecast(Long instanceId);

    /**
     * 查询单指标在指定时间范围内的趋势数据。
     *
     * @param instanceId 实例 ID
     * @param metricCode 指标编码（如 mysql.qps）
     * @param from       开始时间（毫秒时间戳，含）；传 0 或负数时默认为 now-1h
     * @param to         结束时间（毫秒时间戳，含）；传 0 或负数时默认为 now
     * @param frequency  数据频率：{@code "1m"} 分钟级（默认）/ {@code "1h"} 小时级
     * @return 趋势 VO，最多 2000 个点
     */
    MetricTrendVo metricTrend(Long instanceId, String metricCode, long from, long to, String frequency);

    /**
     * 性能分析多指标趋势批量查询：一次返回多个指标在同一时间范围内的趋势序列。
     * <p>小时级数据由 1m 连续聚合降采样（天生小时采集的容量类指标直查 1h 原始表）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码列表
     * @param from        开始时间（毫秒时间戳，含）；传 0 或负数时默认为 now-24h
     * @param to          结束时间（毫秒时间戳，含）；传 0 或负数时默认为 now
     * @param frequency   数据频率：{@code "1m"} 分钟级 / {@code "1h"} 小时级（默认）
     * @return 批量趋势 VO，序列顺序与入参 metricCodes 一致
     */
    PerfTrendBatchVo perfTrendBatch(Long instanceId, List<String> metricCodes, long from, long to, String frequency);

    /**
     * 批量查询多指标最新值（来自 metric_data_1m，10 分钟新鲜窗口）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码列表
     * @return Map(metricCode → 最新值)，无新鲜数据的指标对应 null
     */
    MetricLatestVo latestMetrics(Long instanceId, List<String> metricCodes);

    /**
     * 查询对象级指标 Top N（来自 metric_capacity_object，2 小时新鲜窗口）。
     *
     * @param instanceId 实例 ID
     * @param metricCode 指标编码（如 capacity.total_size_bytes）
     * @param limit      返回条数（1~200，默认 20）
     * @return 对象列表按 value 降序排列
     */
    MetricObjectVo metricObjects(Long instanceId, String metricCode, int limit);

    /**
     * 表 I/O 热点分页（近 1 小时）：按 wait_ms 降序，并合并同表的读写次数。
     *
     * @param instanceId 实例 ID
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页条数
     */
    PageResult<TableIoPageVo> tableIoPage(Long instanceId, long pageNum, long pageSize);

    /**
     * 疑似未使用索引分页（天级文本指标 mysql.index.unused_list 解析后切片）。
     *
     * @param instanceId 实例 ID
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页条数
     */
    UnusedIndexPageVo unusedIndexPage(Long instanceId, long pageNum, long pageSize);

    /**
     * 查询当前长连接明细（来自 metric_long_conn，10 分钟新鲜窗口）。
     *
     * @param instanceId 实例 ID
     * @return 长连接列表按持续时间降序，最多 500 条
     */
    LongConnVo longConnections(Long instanceId);

    /**
     * 查询配置参数当前值（mysql.var.* 数值型 + mysql.var_text.* 文本型，2 天新鲜窗口）。
     *
     * @param instanceId 实例 ID
     * @return 参数当前值列表，无新鲜数据的参数 hasValue=false
     */
    ParamCurrentVo paramsCurrent(Long instanceId);

    /**
     * 配置参数分页查询（合并当前值 + 元数据，支持关键词 / 分类过滤）。
     *
     * @param req 分页请求（instanceId、keyword、category、pageNum、pageSize）
     * @return 分页结果，包含合并后的参数列表及总条数
     */
    PageResult<ParamPageItemVo> paramsPage(ParamPageRequest req);

    /**
     * 查询表级周环比增长 Top N（基于 metric_capacity_object 小时快照，当前 vs 7 天前）。
     *
     * @param instanceId 实例 ID
     * @param metricCode 容量指标编码（如 capacity.total_size_bytes）
     * @param limit      返回条数（1~200，默认 50）
     * @return 按增长字节数降序排列的表列表；无上周数据的表排最后
     */
    TableGrowthVo tableGrowth(Long instanceId, String metricCode, int limit);

    /**
     * 查询今日累计统计（临时表 + 慢查询，汇总 metric_data_1m 今日 delta 值）。
     *
     * @param instanceId 实例 ID
     * @return 今日内存临时表数、磁盘临时表数、慢查询数及磁盘临时表占比
     */
    TodayStatsVo todayStats(Long instanceId);

    /**
     * 批量查询文本指标最新值（1m 或 1d 表，由 frequency 决定）。
     *
     * @param instanceId  实例 ID
     * @param metricCodes 指标编码列表（如 mysql.var_text.sql_mode）
     * @param frequency   频率档位：{@code "1m"} 或 {@code "1d"}（其他值默认 1d）
     * @return Map(metricCode → 最新文本值)，无新鲜数据为 null
     */
    MetricTextVo latestTextMetrics(Long instanceId, List<String> metricCodes, String frequency);

    /**
     * 查询单指标文本变更历史（1d 表，最多 100 条，按时间降序）。
     *
     * @param instanceId 实例 ID
     * @param metricCode 指标编码
     * @return 变更历史列表（含值和时间戳）
     */
    MetricTextVo.HistoryVo textMetricHistory(Long instanceId, String metricCode);

    /**
     * 主机磁盘 IO 按盘趋势：从 host.diskio.detail 分钟级文本历史透视出每块盘的
     * IO 繁忙度（%）、读速率、写速率（B/s）三组序列，按盘名字典序排列。
     *
     * @param instanceId 实例 ID
     * @param from       起始时间（毫秒），&le;0 时默认最近 24 小时
     * @param to         结束时间（毫秒），&le;0 时默认当前时间
     */
    HostDiskIoTrendVo hostDiskIoTrend(Long instanceId, long from, long to);
}
