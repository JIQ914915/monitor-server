package com.lzzh.monitor.service.llm;

import com.lzzh.monitor.api.request.LlmConfigSaveRequest;
import com.lzzh.monitor.api.response.LlmConfigVo;

/** LLM 智能分析全局配置。 */
public interface LlmConfigService {

    /** 查询配置（api_key 只回显掩码）。 */
    LlmConfigVo get();

    /** 保存配置（apiKey 传掩码表示不修改，明文则加密后落库）。 */
    void save(LlmConfigSaveRequest request);
}
