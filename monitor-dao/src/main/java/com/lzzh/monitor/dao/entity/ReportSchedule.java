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

/** 定时报告任务（§11.9）：xxl-job 扫描 next_run 到期的启用任务生成报告归档。 */
@Data
@TableName(value = "report_schedule", autoResultMap = true)
public class ReportSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 报告类型：字典 report_type。 */
    private String reportType;

    /** 巡检周期：字典 report_cycle（仅巡检报告）。 */
    private String cycle;

    /** 范围方式：instance/group/owner。 */
    private String scopeType;

    private String scopeText;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> instanceIds;

    /** 时间范围：字典 report_time_range。 */
    private String timeRange;

    /** 执行频率：字典 report_frequency（daily/weekly/monthly）。 */
    private String frequency;

    /** 执行时间 HH:mm。 */
    private String runTime;

    /** 报告生成后推送的收件邮箱列表（空列表不推送）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> notifyEmails;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime nextRun;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime lastRunTime;

    private Boolean enabled;

    private String createdBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updatedAt;
}
