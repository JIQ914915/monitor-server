package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.api.response.InstanceCapabilityVo;

import java.util.List;

/** 实例运行时能力状态检测（需求 §20：能力矩阵 + 页面降级）。 */
public interface InstanceCapabilityService {

    /**
     * 检测实例各监控能力的运行时状态：
     * 组合数据库版本（版本不支持）、主机关联（不适用）、最近采集日志（采集异常/数据不足）判断，
     * 前端各页面据此渲染降级横幅与引导文案。
     *
     * @param instanceId 实例 ID
     * @return 能力状态列表（含 available 项，前端只提示异常项）
     */
    List<InstanceCapabilityVo> detect(Long instanceId);
}
