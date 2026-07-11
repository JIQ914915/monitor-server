package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.admin.security.SecurityUtils;
import com.lzzh.monitor.api.request.LlmConfigSaveRequest;
import com.lzzh.monitor.api.request.SlowSqlLlmAnalyzeRequest;
import com.lzzh.monitor.api.response.LlmAnalysisVo;
import com.lzzh.monitor.api.response.LlmConfigVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.llm.LlmAnalysisService;
import com.lzzh.monitor.service.llm.LlmConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 智能分析（§11.7.4 可选增强）：配置管理 + 告警事件总结/原因/建议生成。
 * 输出为 AI 生成内容，仅供参考，高风险处置必须人工确认，系统不做任何自动执行。
 */
@Tag(name = "智能分析", description = "LLM 配置管理与告警事件智能分析生成")
@RestController
@RequestMapping("/api/v1/llm")
public class LlmController {

    private final LlmConfigService configService;
    private final LlmAnalysisService analysisService;

    public LlmController(LlmConfigService configService, LlmAnalysisService analysisService) {
        this.configService = configService;
        this.analysisService = analysisService;
    }

    @Operation(summary = "查询智能分析配置", description = "api_key 只回显掩码，从不回显真实值")
    @PostMapping("/config/get")
    @RequiresPerm("llm_config:view")
    public Result<LlmConfigVo> getConfig() {
        return Result.ok(configService.get());
    }

    @Operation(summary = "保存智能分析配置",
            description = "apiKey 传掩码 ****** 表示不修改；数据不出域开关关闭时仅允许内网/本机接口地址")
    @PostMapping("/config/save")
    @RequiresPerm("llm_config:update")
    @OperateLog(module = "智能分析", action = "保存配置")
    public Result<Void> saveConfig(@RequestBody LlmConfigSaveRequest request) {
        configService.save(request);
        return Result.ok();
    }

    @Operation(summary = "查询事件已生成的智能分析", description = "未生成过返回空数据")
    @PostMapping("/analysis/get")
    @RequiresPerm("alert_rule:view")
    public Result<LlmAnalysisVo> getAnalysis(@RequestBody AnalyzeRequest request) {
        return Result.ok(analysisService.getByEvent(request.getEventId()));
    }

    @Operation(summary = "生成事件智能分析",
            description = "组装事件上下文（按配置脱敏）调用大模型生成总结/可能原因/处理建议，"
                    + "结果按事件缓存；regenerate=true 强制重新生成。调用元数据落库并记录操作审计")
    @PostMapping("/analyze")
    @RequiresPerm("llm:analyze")
    @OperateLog(module = "智能分析", action = "生成事件分析")
    public Result<LlmAnalysisVo> analyze(@RequestBody AnalyzeRequest request) {
        var u = SecurityUtils.current();
        return Result.ok(analysisService.analyze(
                request.getEventId(), Boolean.TRUE.equals(request.getRegenerate()), u.id(), u.username()));
    }

    @Operation(summary = "慢SQL指纹智能分析",
            description = "服务端重查指纹窗口统计并组装样本 SQL（按配置脱敏）调用大模型，"
                    + "生成性能问题总结/慢因分析/优化建议。按需生成不缓存，调用记录操作审计")
    @PostMapping("/slowsql/analyze")
    @RequiresPerm("llm:analyze")
    @OperateLog(module = "智能分析", action = "生成慢SQL分析")
    public Result<LlmAnalysisVo> analyzeSlowSql(@Valid @RequestBody SlowSqlLlmAnalyzeRequest request) {
        var u = SecurityUtils.current();
        return Result.ok(analysisService.analyzeSlowSql(request, u.id(), u.username()));
    }

    /** 分析请求体。 */
    @Data
    public static class AnalyzeRequest {
        private Long eventId;
        private Boolean regenerate;
    }
}
