package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 告警事件（§21.2.2 + §11.6）。同对象未恢复事件按 dedupKey 归并去重。 */
@Data
@TableName(value = "alert_event", autoResultMap = true)
public class AlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件编码（建单时生成，唯一）。 */
    private String eventCode;

    private Long ruleId;

    /** 规则名称（冗余快照）。 */
    private String ruleName;

    /** 规则级别：字典 alert_level（level_1~level_4，一级最高）。 */
    private String ruleLevel;

    private Long instanceId;

    /** 实例名称（冗余快照）。 */
    private String instanceName;

    private String triggerValue;

    private String thresholdValue;

    /** 告警信息（由规则模板渲染后的快照，供列表展示与通知复用）。 */
    private String alertMessage;

    /** 对象维度键（SQL digest/表/复制通道/表空间；无对象维度为空）。 */
    private String dimensionKey;

    /** 归并键=rule_code+instance_id+dimension_key，用于活跃事件归并去重。 */
    private String dedupKey;

    /** 事件来源（字典 event_source）：rule=告警规则 / scenario=场景综合诊断 / system=系统事件。 */
    private String eventSource;

    /** 场景来源事件对应的 monitor_scenario.scenario_code，其余来源为空。 */
    private String scenarioCode;

    /** 场景事件触发时各信号快照：[{"code","name","expr","currentVal","met"}]。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> signalsSnapshot;

    /** 阻塞链现场快照（锁相关事件建单时即席抓取）：{capturedAt,dbVersion,total,error,rows[]}。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> blockingChainSnapshot;

    /** 归并后触发次数。 */
    private Integer triggerCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime lastTriggerTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime triggerTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime recoveryTime;

    /** 状态：pending/confirmed/handling/recovered/closed/ignored。 */
    private String status;

    /** 处理人。 */
    private String assignee;

    /** 最近一次处置备注（confirm/handling/silence/close 时填写）。 */
    private String lastRemark;

    /** 确认执行人用户ID。 */
    private Long confirmUserId;

    /** 静默执行人用户ID。 */
    private Long silenceUserId;

    /** 关闭执行人用户ID。 */
    private Long closeUserId;

    /** 静默到期时间（窗口期结束后再次触发可重新建单）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime silenceUntilTime;

    /**
     * 最近一次通知派发时间（记录已落库并提交异步发送/重试队列），并非"确认送达"时间；
     * 真实送达结果以 {@code alert_notify_record.status}（pending/sending/success/failed/dead）为准。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime lastNotifyTime;

    /** 已派发通知次数（用于重复通知静默期判断），非"确认送达成功"次数，语义同 {@link #lastNotifyTime}。 */
    private Integer notifyCount;

    /** 最近评估状态：normal/metric_missing 等，不替代事件生命周期 status。 */
    private String evalState;

    /** 最近评估说明，如指标缺失原因。 */
    private String evalMessage;

    /** 最近一次规则评估时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime lastEvalTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updatedAt;
}
