package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.AlertNotifyChannelSaveRequest;
import com.lzzh.monitor.api.response.AlertNotifyChannelVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.alert.AlertNotifyChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警通知通道全局配置（系统管理 → 通知通道）。
 *
 * <p>Webhook/钉钉/企业微信/飞书机器人的地址与签名密钥统一在此维护，
 * 告警规则里只做通道勾选，避免每个实例逐条维护参数。
 */
@Tag(name = "通知通道", description = "告警通知通道全局配置的查询与保存")
@RestController
@RequestMapping("/api/v1/alerts/notify-channels")
public class NotifyChannelController {

    private final AlertNotifyChannelService channelService;

    public NotifyChannelController(AlertNotifyChannelService channelService) {
        this.channelService = channelService;
    }

    /**
     * 查询全部通道配置。
     *
     * @return 四个通道（webhook/dingtalk/wecom/feishu）的全局配置，密钥掩码回显
     */
    @Operation(summary = "查询通知通道配置", description = "返回全部通道的全局配置，签名密钥仅回显掩码")
    @GetMapping("/list")
    @RequiresPerm("notify_channel:list")
    public Result<List<AlertNotifyChannelVo>> list() {
        return Result.ok(channelService.list());
    }

    /**
     * 批量保存通道配置（按 channel upsert）。
     *
     * @param configs 通道配置列表；密钥传掩码表示不变，空字符串表示清除
     * @return 空响应体
     */
    @Operation(summary = "保存通知通道配置", description = "批量保存通道全局配置，签名密钥加密存储")
    @PostMapping("/save")
    @RequiresPerm("notify_channel:update")
    @OperateLog(module = "通知通道", action = "保存配置")
    public Result<Void> save(@RequestBody @Valid List<AlertNotifyChannelSaveRequest> configs) {
        channelService.save(configs);
        return Result.ok();
    }
}
