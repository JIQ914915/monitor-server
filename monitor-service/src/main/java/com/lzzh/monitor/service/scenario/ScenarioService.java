package com.lzzh.monitor.service.scenario;

import com.lzzh.monitor.api.request.ScenarioDetailRequest;
import com.lzzh.monitor.api.request.ScenarioPageRequest;
import com.lzzh.monitor.api.request.ScenarioThresholdRequest;
import com.lzzh.monitor.api.request.ScenarioToggleRequest;
import com.lzzh.monitor.api.response.ScenarioPageVo;
import com.lzzh.monitor.api.response.ScenarioVo;

/** 场景管理服务（§11.8）：实例级场景列表（实时信号状态）、详情、启停。 */
public interface ScenarioService {

    /** 适配当前实例的场景列表 + 统计卡片数据，含各信号实时求值状态。 */
    ScenarioPageVo page(ScenarioPageRequest req);

    /** 场景详情：列表字段 + 诊断结论 + 关联知识库文章。 */
    ScenarioVo detail(ScenarioDetailRequest req);

    /** 场景启停（写 scenario_instance_config）；停用时联动关闭该场景在此实例的活跃综合事件。 */
    boolean toggle(ScenarioToggleRequest req);

    /** 实例级阈值调整：仅覆盖信号触发阈值数值，运算符与组合逻辑不可改；空 overrides 恢复模板默认。 */
    boolean updateThresholds(ScenarioThresholdRequest req);

    /**
     * 一键开启系统推荐的常用场景（recommended=true，按实例适配过滤；
     * 未关联主机的实例跳过 scenario.host_* 场景）。已启用的跳过，不覆盖已有配置。
     *
     * @return 本次新开启的场景数
     */
    int enableRecommended(Long instanceId);
}
