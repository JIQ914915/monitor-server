package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 告警通知通道全局配置 VO。 */
@Data
@Schema(description = "告警通知通道全局配置 VO")
public class AlertNotifyChannelVo {

    @Schema(description = "通道：webhook/dingtalk/wecom/feishu")
    private String channel;

    @Schema(description = "全局开关：关闭后勾选了该通道的规则不再发送")
    private Boolean enabled;

    @Schema(description = "通知地址列表")
    private List<String> urls;

    @Schema(description = "签名密钥（钉钉/飞书；已配置时回显掩码 ******，从不回显真实密钥）")
    private String secret;

    @Schema(description = "最近更新时间")
    private String updatedAt;
}
