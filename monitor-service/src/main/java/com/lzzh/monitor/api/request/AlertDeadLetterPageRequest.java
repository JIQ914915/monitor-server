package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 告警通知死信分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "告警通知死信分页查询请求")
public class AlertDeadLetterPageRequest extends PageParam {

    @Schema(description = "实例 ID（不传则查全部有权限实例）")
    private Long instanceId;

    @Schema(description = "通道过滤（webhook/email/sms/dingtalk/wecom/feishu），不传则不限")
    private String channel;
}
