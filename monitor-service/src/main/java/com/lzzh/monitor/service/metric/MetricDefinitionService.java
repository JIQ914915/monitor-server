package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.dao.entity.MetricDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 指标定义服务（P1-2）：metric_definition 元数据的单一访问点。
 * <p>采集写入、前端展示、健康评分等下游一律通过此服务获取指标口径，
 * 不再硬编码 metricCode 语义（valueType/unit/frequency/domain 等）。
 * 内部维护热加载内存缓存，避免高频采集路径打库。
 */
public interface MetricDefinitionService {

    /**
     * 查询指定 dbType 的全部启用指标定义（已缓存，按 metricCode 字典序）。
     *
     * @param dbType 数据库类型（如 mysql，大小写不敏感）
     * @return 该 dbType 的指标定义列表
     */
    List<MetricDefinition> listByDbType(String dbType);

    /**
     * 按 metricCode 精确查找定义（缓存命中，O(1)）。
     *
     * @param metricCode 指标编码
     * @return 指标定义，不存在返回 empty
     */
    Optional<MetricDefinition> findByCode(String metricCode);

    /**
     * 查询全部指标定义（管理视图 + 前端口径注册，含所有 dbType）。
     *
     * @return 全部指标定义列表
     */
    List<MetricDefinition> listAll();

    /**
     * 主动刷新内存缓存（新增/修改指标定义后调用）。
     */
    void refreshCache();
}
