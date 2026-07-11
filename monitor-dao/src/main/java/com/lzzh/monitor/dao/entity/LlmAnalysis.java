package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 告警事件 LLM 智能分析结果（每事件缓存最新一次，重新生成覆盖，§11.7.4）。
 * 输出为 AI 生成的总结/原因/建议，页面须标注"仅供参考"；调用元数据兼作审计。
 */
@Data
@TableName(value = "llm_analysis", autoResultMap = true)
public class LlmAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Boolean success;

    /** 事件总结（一段话）。 */
    private String summary;

    /** 可能原因列表。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> causes;

    /** 处理建议列表（仅建议，须人工确认后执行）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> suggestions;

    private String errorMessage;

    private String model;

    private Integer promptChars;

    private Integer responseChars;

    private Long durationMs;

    private Long operatorId;

    private String operatorName;

    private OffsetDateTime createdAt;
}
