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
 * 告警通知通道全局配置：Webhook/钉钉/企业微信/飞书机器人的地址与签名密钥统一在此维护，
 * 告警规则只做通道勾选。
 */
@Data
@TableName(value = "alert_notify_channel_config", autoResultMap = true)
public class AlertNotifyChannelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 通道：webhook/dingtalk/wecom/feishu。 */
    private String channel;

    /** 全局开关：关闭后勾选了该通道的规则不再发送。 */
    private Boolean enabled;

    /** 通道参数：{@code urls}=地址列表；{@code secret}=签名密钥（钉钉/飞书，PasswordCipher 加密存储）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
