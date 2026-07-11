package com.lzzh.monitor.service.llm;

import com.lzzh.monitor.api.request.LlmConfigSaveRequest;
import com.lzzh.monitor.api.response.LlmConfigVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.security.PasswordCipher;
import com.lzzh.monitor.dao.entity.LlmConfig;
import com.lzzh.monitor.dao.mapper.LlmConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

/**
 * LLM 智能分析配置实现（单行，id 固定 1）。
 * api_key 经 {@link PasswordCipher} 加密落库，查询只回显掩码 ******，从不回显真实值。
 */
@Service
public class LlmConfigServiceImpl implements LlmConfigService {

    /** 掩码：已配置密钥的回显值；保存时收到掩码表示"不修改"。 */
    static final String SECRET_MASK = "******";
    private static final long CONFIG_ID = 1L;

    private final LlmConfigMapper configMapper;
    private final PasswordCipher passwordCipher;

    public LlmConfigServiceImpl(LlmConfigMapper configMapper, PasswordCipher passwordCipher) {
        this.configMapper = configMapper;
        this.passwordCipher = passwordCipher;
    }

    @Override
    public LlmConfigVo get() {
        LlmConfig c = loadOrInit();
        LlmConfigVo vo = new LlmConfigVo();
        vo.setEnabled(Boolean.TRUE.equals(c.getEnabled()));
        vo.setBaseUrl(c.getBaseUrl());
        vo.setApiKey(StringUtils.hasText(c.getApiKey()) ? SECRET_MASK : "");
        vo.setModel(c.getModel());
        vo.setTimeoutSeconds(c.getTimeoutSeconds());
        vo.setAllowExternal(Boolean.TRUE.equals(c.getAllowExternal()));
        vo.setDesensitize(!Boolean.FALSE.equals(c.getDesensitize()));
        return vo;
    }

    @Override
    public void save(LlmConfigSaveRequest request) {
        if (Boolean.TRUE.equals(request.getEnabled()) && !StringUtils.hasText(request.getBaseUrl())) {
            throw new BusinessException("启用智能分析前请先填写接口地址");
        }
        LlmConfig c = loadOrInit();
        c.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        c.setBaseUrl(trimTrailingSlash(request.getBaseUrl()));
        c.setModel(request.getModel());
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() > 0) {
            c.setTimeoutSeconds(Math.min(request.getTimeoutSeconds(), 300));
        }
        c.setAllowExternal(Boolean.TRUE.equals(request.getAllowExternal()));
        c.setDesensitize(!Boolean.FALSE.equals(request.getDesensitize()));
        String key = request.getApiKey();
        if (key != null && !SECRET_MASK.equals(key)) {
            // 掩码=不修改；空串=清除；明文=加密落库
            c.setApiKey(StringUtils.hasText(key) ? passwordCipher.encrypt(key.trim()) : null);
        }
        c.setUpdatedAt(OffsetDateTime.now());
        configMapper.updateById(c);
    }

    /** 供分析服务读取原始配置（含密文 key）。 */
    LlmConfig loadOrInit() {
        LlmConfig c = configMapper.selectById(CONFIG_ID);
        if (c == null) {
            c = new LlmConfig();
            c.setId(CONFIG_ID);
            c.setEnabled(false);
            c.setTimeoutSeconds(60);
            c.setAllowExternal(false);
            c.setDesensitize(true);
            c.setUpdatedAt(OffsetDateTime.now());
            configMapper.insert(c);
        }
        return c;
    }

    private static String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
