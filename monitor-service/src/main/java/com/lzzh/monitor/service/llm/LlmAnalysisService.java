package com.lzzh.monitor.service.llm;

import com.lzzh.monitor.api.response.LlmAnalysisVo;

/** 告警事件 LLM 智能分析（§11.7.4：仅生成总结/原因/建议，不做自动处置）。 */
public interface LlmAnalysisService {

    /** 查询事件已生成的分析结果；未生成过返回 null。 */
    LlmAnalysisVo getByEvent(Long eventId);

    /**
     * 生成（或重新生成）事件智能分析：组装事件上下文 → 按配置脱敏 →
     * 调用 OpenAI 兼容接口 → 结果按事件缓存。
     *
     * @param regenerate false 时若已有缓存直接返回
     */
    LlmAnalysisVo analyze(Long eventId, boolean regenerate, Long operatorId, String operatorName);

    /**
     * 慢 SQL 指纹智能分析：组装指纹窗口统计 + 样本 SQL（脱敏）→ 调用大模型生成
     * 性能问题总结/慢因分析/优化建议。结果不落库（按需生成），调用走操作审计。
     */
    LlmAnalysisVo analyzeSlowSql(com.lzzh.monitor.api.request.SlowSqlLlmAnalyzeRequest request,
                                 Long operatorId, String operatorName);
}
