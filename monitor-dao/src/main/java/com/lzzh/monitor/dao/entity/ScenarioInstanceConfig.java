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
 * 场景实例级配置。
 * <p>场景默认停用：无本表记录或 {@code enabled=false} 即不评估（同内置告警规则模式）。
 */
@Data
@TableName(value = "scenario_instance_config", autoResultMap = true)
public class ScenarioInstanceConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scenarioCode;

    private Long instanceId;

    private Boolean enabled;

    /** 该实例上此场景累计触发次数。 */
    private Long triggerCount;

    /** 实例级阈值覆盖：{信号code: 阈值}，仅覆盖 threshold 数值；NULL=使用场景模板默认阈值。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> conditionOverrides;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
