package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 告警规则实例级配置（内置模板实例化参数）。
 * <p>内置规则仅当存在本表记录且 {@code enabled=true} 才视为启用；删除记录即恢复默认停用。
 */
@Data
@TableName(value = "alert_rule_instance_config", autoResultMap = true)
public class AlertRuleInstanceConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码：内置规则=alert_rule.rule_code；自定义规则=custom.<uuid>。 */
    private String ruleCode;

    private Long instanceId;

    /** 规则类型：builtin/custom。 */
    private String ruleType;

    /** 是否启用（覆盖全局默认值）。 */
    private Boolean enabled;

    /** 实例级扫描间隔（分钟），为空继承模板。 */
    private Integer scanIntervalMin;

    /** 实例级指标覆盖，为空继承模板。 */
    private String metricName;

    /** 实例级规则名称快照（内置规则启用时复制模板，禁止用户修改）。 */
    private String ruleName;

    /** 实例级告警级别覆盖，为空继承模板。 */
    private String ruleLevel;

    /** 实例级描述覆盖，为空继承模板。 */
    private String description;

    /** 实例级触发条件覆盖。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> conditionConfig;

    /** 实例级恢复条件覆盖。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> recoveryConfig;

    /** 实例级通知配置覆盖。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> notificationConfig;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
