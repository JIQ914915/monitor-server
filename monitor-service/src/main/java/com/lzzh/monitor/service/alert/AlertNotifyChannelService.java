package com.lzzh.monitor.service.alert;

import com.lzzh.monitor.api.request.AlertNotifyChannelSaveRequest;
import com.lzzh.monitor.api.response.AlertNotifyChannelVo;

import java.util.List;

/** 告警通知通道全局配置：规则只勾选通道，地址/密钥统一在此维护。 */
public interface AlertNotifyChannelService {

    /** 查询全部通道配置（密钥掩码回显）。 */
    List<AlertNotifyChannelVo> list();

    /** 批量保存通道配置（按 channel upsert，密钥加密存储）。 */
    void save(List<AlertNotifyChannelSaveRequest> configs);
}
