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

/**
 * 监控场景模板（§11.8 场景化监控）。
 * <p>场景 = 多信号 AND/OR 复合诊断：conditionConfig 为条件组树，触发后生成综合告警事件
 * （alert_event.event_source=scenario），并携带信号快照与诊断结论。
 */
@Data
@TableName(value = "monitor_scenario", autoResultMap = true)
public class MonitorScenario {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景编码（唯一），如 scenario.connection_pool_exhaustion。 */
    private String scenarioCode;

    private String scenarioName;

    private String description;

    /** 级别：字典 alert_level（level_1~level_4）。 */
    private String severity;

    /** 适用数据库类型（FK → database_type.id）。 */
    private Long dbTypeId;

    /** 适用版本 ID 列表，null 表示全版本。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> dbVersionIds;

    /**
     * 条件组树：{@code {"logic":"AND|OR","duration":秒,"children":[...]}}；
     * children 元素为 condition（threshold/rate_change）或嵌套 group。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> conditionConfig;

    /** 恢复配置：{@code {"duration":秒}}，触发逻辑不满足持续 N 秒后恢复。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> recoveryConfig;

    /** 触发时默认诊断结论。 */
    private String diagnosisTemplate;

    /** 分支诊断结论：[{"when":["condCode",...],"text":"..."}]，when 内信号全部命中时匹配。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> diagnosisBranches;

    /** 关联知识库文章 id 数组。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> knowledgeArticleIds;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> notificationConfig;

    private Integer scanIntervalMin;

    private Boolean builtin;

    /** 系统推荐的常用场景（「一键开启常用」圈选范围）。 */
    private Boolean recommended;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updatedAt;
}
