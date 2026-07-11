package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.HealthScoreVo;

/** 实例健康评分计算服务。 */
public interface HealthScoreService {

    /**
     * 计算指定实例的健康评分。
     *
     * <p>读取最近时间窗口内的采集指标，按五维度加权公式计算综合健康分：
     * 可用性(30%) + 性能(25%) + 稳定性(20%) + 容量(15%) + 安全配置(10%)。
     *
     * @param instanceId 实例 ID
     * @return 健康评分 VO；若实例无任何新鲜采集数据，返回 score=-1, level="no_data"
     */
    HealthScoreVo calculate(Long instanceId);
}
