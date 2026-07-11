package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 指标定义/元数据表（§21.2.2）。
 * <p>数值/文本落表路由、单位格式化与指标释义的单一事实来源：写入与查询依据 {@code valueType}
 * 将 {@code metricCode} 落到 metric_data_*（numeric）或 metric_text_data_*（text）。
 * <p>时间列为 TIMESTAMPTZ，映射为 {@link OffsetDateTime}（PG 驱动无法将 timestamptz 直接转 LocalDateTime，见 V17）。
 */
@Data
@TableName("metric_definition")
public class MetricDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 指标编码，如 mysql.connection.usage_percent。 */
    private String metricCode;

    /** 指标名称，如 连接使用率。 */
    private String metricName;

    /** 数据库类型（mysql/oracle/...）。 */
    private String dbType;

    /** 指标域（connection/traffic/sql/lock/innodb/replication/capacity/security/collector）。 */
    private String domain;

    /** 分层：guard 值守 / analysis 分析 / explain 解释。 */
    private String layer;

    /** 值类型：numeric 数值 / text 文本状态（决定落表路由）。 */
    private String valueType;

    /** 单位（percent/count/bytes/ms/qps...），前端格式化用。 */
    private String unit;

    /** 来源采集器编码（如 mysql.global_status），仅作文档标注，不关联运行时逻辑。 */
    private String sourceCollector;

    /** 加工类型（raw/delta/ratio/agg/trend/state/score，与 §9.1 对应）。 */
    private String processType;

    /** 采集频率（1m/1h/1d），辅助路由到 metric_data_<频率>。 */
    private String frequency;

    /** 指标释义（是什么/单位/如何解读），前端 Tooltip；场景诊断归知识库。 */
    private String description;

    private Boolean enabled;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updatedAt;
}
