package com.lzzh.monitor.service.alert;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lzzh.monitor.api.request.AlertNotifyChannelSaveRequest;
import com.lzzh.monitor.api.response.AlertNotifyChannelVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.security.PasswordCipher;
import com.lzzh.monitor.dao.entity.AlertNotifyChannelConfig;
import com.lzzh.monitor.dao.mapper.AlertNotifyChannelConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 告警通知通道全局配置实现。
 *
 * <p>config JSONB 结构：{@code {"urls":[...],"secret":"enc:..."}}。
 * 签名密钥（钉钉/飞书）经 {@link PasswordCipher} 加密落库，查询时只回显掩码，从不回显真实值。
 */
@Service
public class AlertNotifyChannelServiceImpl implements AlertNotifyChannelService {

    /** 密钥掩码占位符：回显用；保存时收到该值表示"未修改，保持原值"。 */
    private static final String MASKED_SECRET = "******";

    private static final String KEY_URLS = "urls";
    private static final String KEY_SECRET = "secret";

    /** 支持全局配置的通道（邮件/短信的服务端参数在部署配置中维护，不在此列）。 */
    private static final Set<String> CHANNELS = Set.of("webhook", "dingtalk", "wecom", "feishu");

    /** 支持签名密钥的通道。 */
    private static final Set<String> SECRET_CHANNELS = Set.of("dingtalk", "feishu");

    /** 通道展示顺序。 */
    private static final List<String> CHANNEL_ORDER = List.of("webhook", "dingtalk", "wecom", "feishu");

    private final AlertNotifyChannelConfigMapper channelMapper;
    private final PasswordCipher passwordCipher;

    public AlertNotifyChannelServiceImpl(AlertNotifyChannelConfigMapper channelMapper, PasswordCipher passwordCipher) {
        this.channelMapper = channelMapper;
        this.passwordCipher = passwordCipher;
    }

    @Override
    public List<AlertNotifyChannelVo> list() {
        Map<String, AlertNotifyChannelConfig> byChannel = new HashMap<>();
        for (AlertNotifyChannelConfig c : channelMapper.selectList(null)) {
            byChannel.put(c.getChannel(), c);
        }
        List<AlertNotifyChannelVo> result = new ArrayList<>();
        for (String channel : CHANNEL_ORDER) {
            AlertNotifyChannelConfig cfg = byChannel.get(channel);
            AlertNotifyChannelVo vo = new AlertNotifyChannelVo();
            vo.setChannel(channel);
            vo.setEnabled(cfg != null && Boolean.TRUE.equals(cfg.getEnabled()));
            vo.setUrls(cfg == null ? List.of() : urlsOf(cfg.getConfig()));
            vo.setSecret(cfg != null && StringUtils.hasText(secretOf(cfg.getConfig())) ? MASKED_SECRET : null);
            if (cfg != null && cfg.getUpdatedAt() != null) {
                vo.setUpdatedAt(cfg.getUpdatedAt().toString());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(List<AlertNotifyChannelSaveRequest> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (AlertNotifyChannelSaveRequest req : configs) {
            String channel = req.getChannel() == null ? null : req.getChannel().trim().toLowerCase();
            if (channel == null || !CHANNELS.contains(channel)) {
                throw new BusinessException("不支持的通知通道：" + req.getChannel());
            }
            AlertNotifyChannelConfig existing = channelMapper.selectOne(
                    Wrappers.<AlertNotifyChannelConfig>lambdaQuery()
                            .eq(AlertNotifyChannelConfig::getChannel, channel));

            Map<String, Object> config = existing != null && existing.getConfig() != null
                    ? new HashMap<>(existing.getConfig()) : new HashMap<>();
            config.put(KEY_URLS, normalizeUrls(req.getUrls()));
            mergeSecret(config, channel, req.getSecret());

            if (existing == null) {
                AlertNotifyChannelConfig entity = new AlertNotifyChannelConfig();
                entity.setChannel(channel);
                entity.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
                entity.setConfig(config);
                channelMapper.insert(entity);
            } else {
                existing.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
                existing.setConfig(config);
                channelMapper.updateById(existing);
            }
        }
    }

    /** 去空白、去重，保持顺序。 */
    private List<String> normalizeUrls(List<String> urls) {
        if (urls == null) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String url : urls) {
            if (StringUtils.hasText(url)) {
                distinct.add(url.trim());
            }
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 密钥合并语义：null 或掩码占位 = 保持原值；空字符串 = 清除；其余 = 加密后覆盖。
     * 不支持密钥的通道直接移除该键。
     */
    private void mergeSecret(Map<String, Object> config, String channel, String incoming) {
        if (!SECRET_CHANNELS.contains(channel)) {
            config.remove(KEY_SECRET);
            return;
        }
        if (incoming == null || MASKED_SECRET.equals(incoming.trim())) {
            return;
        }
        String trimmed = incoming.trim();
        if (trimmed.isEmpty()) {
            config.remove(KEY_SECRET);
        } else {
            config.put(KEY_SECRET, passwordCipher.encrypt(trimmed));
        }
    }

    private List<String> urlsOf(Map<String, Object> config) {
        Object raw = config == null ? null : config.get(KEY_URLS);
        if (raw instanceof List<?> list) {
            List<String> urls = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s && StringUtils.hasText(s)) {
                    urls.add(s);
                }
            }
            return urls;
        }
        return List.of();
    }

    private String secretOf(Map<String, Object> config) {
        Object raw = config == null ? null : config.get(KEY_SECRET);
        return raw instanceof String s ? s : null;
    }
}
