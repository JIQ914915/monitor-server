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

/** 报告归档（§11.9）：巡检/性能/告警三类报告，生成时按真实数据分段落库。 */
@Data
@TableName(value = "monitor_report", autoResultMap = true)
public class MonitorReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报告编码（如 INSP-1720000000000，唯一）。 */
    private String reportCode;

    private String title;

    /** 报告类型：字典 report_type（inspection/performance/alert）。 */
    private String reportType;

    /** 巡检周期：字典 report_cycle（daily/weekly/monthly/special，仅巡检报告）。 */
    private String cycle;

    /** 范围方式：instance/group/owner。 */
    private String scopeType;

    /** 范围描述快照。 */
    private String scopeText;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> instanceIds;

    /** 时间范围：字典 report_time_range（24h/7d/30d）。 */
    private String timeRange;

    /** 生成方式：字典 report_gen_mode（manual/schedule）。 */
    private String genMode;

    /** 状态：字典 report_status。 */
    private String status;

    /** 报告正文 {"sections":[...]}。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> content;

    private String createdBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime generateTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;
}
