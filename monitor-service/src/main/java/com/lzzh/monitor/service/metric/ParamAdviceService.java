package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.ParamAdviceVo;

import java.util.List;

/** 参数调优建议（规则化体检，只输出建议，调整须人工确认）。 */
public interface ParamAdviceService {

    /** 生成实例的参数调优建议列表；无建议返回空列表（配置健康）。 */
    List<ParamAdviceVo> advise(Long instanceId);
}
