package com.lzzh.monitor.common.enums;

/**
 * 实例运行状态常量。
 * <p>取值与 {@code sys_dict}（dict_type = instance_status，见 V18 迁移）保持一致，
 * 由字典统一维护展示名称/颜色/排序；代码中仅引用值本身，禁止散落硬编码字符串。
 * <ul>
 *   <li>{@link #NORMAL}：正常，采集与告警均生效；</li>
 *   <li>{@link #ABNORMAL}：连接异常，由采集器在连续失败达阈值后自动标记、连接恢复后自动还原；</li>
 *   <li>{@link #PAUSED}：人工暂停采样，采集调度直接跳过，采集器不得覆盖该状态。</li>
 * </ul>
 */
public final class InstanceStatus {

    public static final String NORMAL = "normal";
    public static final String ABNORMAL = "abnormal";
    public static final String PAUSED = "paused";

    private InstanceStatus() {
    }
}
