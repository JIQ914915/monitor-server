package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 告警通知通道全局配置保存请求（按 channel upsert）。 */
@Data
@Schema(description = "告警通知通道全局配置保存请求")
public class AlertNotifyChannelSaveRequest {

    @NotBlank(message = "通道不能为空")
    @Schema(description = "通道：webhook/dingtalk/wecom/feishu", requiredMode = Schema.RequiredMode.REQUIRED)
    private String channel;

    @Schema(description = "全局开关")
    private Boolean enabled;

    @Schema(description = "通知地址列表")
    private List<String> urls;

    @Schema(description = "签名密钥（钉钉/飞书）：不传或传掩码 ****** 表示不变，空字符串表示清除")
    private String secret;
}
