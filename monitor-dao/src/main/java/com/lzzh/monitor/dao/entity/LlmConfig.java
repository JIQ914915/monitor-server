package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * LLM 智能分析全局配置（单行，id 固定 1，§11.7.4）。
 * api_key 经 PasswordCipher 加密（enc: 前缀），查询只回显掩码。
 */
@Data
@TableName("llm_config")
public class LlmConfig {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Boolean enabled;

    /** OpenAI 兼容接口地址（到 /v1 为止）。 */
    private String baseUrl;

    /** API Key（enc: 密文；本地部署可为空）。 */
    private String apiKey;

    private String model;

    private Integer timeoutSeconds;

    /** 数据不出域开关：false=仅允许内网/本机地址（默认）。 */
    private Boolean allowExternal;

    /** 发送前对上下文脱敏（IP 打码、SQL 字面量参数化）。 */
    private Boolean desensitize;

    private OffsetDateTime updatedAt;
}
