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
 * 告警下钻画像（§11.7 事件下钻与报告）。
 * <p>按告警触发指标编码匹配画像，驱动下钻页四块内容：
 * 关联指标 / 可能原因 / 排查路径 / 建议动作。
 * 匹配规则：exact 优先于 prefix，prefix 长者优先；均未命中回退 generic（match_rules 为空的兜底画像）。
 */
@Data
@TableName(value = "alert_drilldown_profile", autoResultMap = true)
public class AlertDrilldownProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 画像编码（唯一），如 connections / slowsql / generic。 */
    private String profileCode;

    /** 展示名，如「连接与会话类」。 */
    private String profileLabel;

    /** 适用数据库类型（预留多类型扩展，当前 mysql）。 */
    private String dbType;

    /** 匹配规则：[{"matchType":"exact|prefix","pattern":"mysql.conn."}]，空数组=仅作兜底。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> matchRules;

    /** 关联指标：[{"code","label","unit","color","toGB"}]。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> relatedMetrics;

    /** 可能原因：[{"cause","confidence","color","evidence":[...]}]。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> causes;

    /** 排查路径：[{"title","description","action","link"}]，link 为页面编码。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> steps;

    /** 建议动作：[{"action","risk","description","sql","impact"}]，仅辅助决策不自动执行。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> actions;

    /** 内置画像不可删除。 */
    private Boolean builtin;

    private Boolean enabled;

    private Integer sort;

    private String remark;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updatedAt;
}
