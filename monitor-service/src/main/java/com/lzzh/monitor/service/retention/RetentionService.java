package com.lzzh.monitor.service.retention;

import com.lzzh.monitor.api.request.RetentionRequest;
import com.lzzh.monitor.api.response.RetentionVo;

import java.util.List;
import java.util.Map;

/** 数据保留策略服务（系统级、6 类，§12.2）。 */
public interface RetentionService {

    /**
     * 出厂默认（天）：与 V2 种子一致。
     *
     * @return 类别到默认保留天数的映射
     */
    Map<String, Integer> factory();

    /**
     * 当前全部类别配置。
     *
     * @return 数据保留策略列表
     */
    List<RetentionVo> list();

    /**
     * 批量保存（按 category upsert），并把变更后的天数下发为真实 TimescaleDB 保留策略。
     *
     * @param configs 各类别保留策略配置列表
     */
    void save(List<RetentionRequest> configs);

    /** 启动时将 retention_config 现有配置同步为真实 TimescaleDB 保留策略（幂等）。 */
    void syncPoliciesFromConfig();
}
