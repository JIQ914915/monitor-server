package com.lzzh.monitor.service.llm;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.SlowSqlDigestDetailRequest;
import com.lzzh.monitor.api.request.SlowSqlLlmAnalyzeRequest;
import com.lzzh.monitor.api.response.LlmAnalysisVo;
import com.lzzh.monitor.api.response.SlowSqlDigestVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.security.PasswordCipher;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.dao.entity.LlmAnalysis;
import com.lzzh.monitor.dao.entity.LlmConfig;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleMapper;
import com.lzzh.monitor.dao.mapper.LlmAnalysisMapper;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.metric.SlowSqlQueryService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 告警事件 LLM 智能分析实现。
 *
 * <p>安全边界（§14 产品定位）：
 * <ul>
 *   <li><b>数据不出域</b>：默认只允许调用内网/本机地址的 OpenAI 兼容服务
 *       （本地 Ollama/vLLM 等）；公网服务须显式打开 allow_external 开关；</li>
 *   <li><b>脱敏</b>：发送前对上下文做 IP 打码与 SQL 字面量参数化（可关闭）；</li>
 *   <li><b>只出建议</b>：提示词明确要求不给出可直接执行的破坏性命令，
 *       输出落库并在页面标注"AI 生成，仅供参考"；</li>
 *   <li><b>审计</b>：调用元数据（模型/字符数/耗时/操作人）随结果落库，
 *       接口层再记一条 sys_oper_log 操作审计。</li>
 * </ul>
 */
@Service
public class LlmAnalysisServiceImpl implements LlmAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LlmAnalysisServiceImpl.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SYSTEM_PROMPT = """
            你是一名资深数据库 DBA（精通 MySQL 与 PostgreSQL），负责协助运维人员分析数据库监控告警事件。
            请按上下文中给出的「数据库类型」使用对应数据库的术语、系统视图与方言进行分析。
            请根据给出的告警事件上下文，输出 JSON（不要用 markdown 代码块包裹），结构如下：
            {"summary":"一段话总结发生了什么、影响是什么，面向不熟悉数据库的运维人员，通俗易懂",
             "causes":["可能原因1（按可能性从高到低排序，最多4条）","..."],
             "suggestions":["排查或处理建议1（只给排查思路与建议，不要给出可直接执行的删除/重启/杀会话等高风险命令，提醒需人工确认）","..."]}
            要求：全部使用中文；结合触发值与阈值给出量化判断；没有把握的不要臆造。
            """;

    private static final String SLOW_SQL_PROMPT = """
            你是一名资深 SQL 优化专家（精通 MySQL 与 PostgreSQL），负责协助 DBA 分析慢 SQL。
            请按上下文中给出的「数据库类型」使用对应数据库的方言与优化手段
            （如 MySQL 的 InnoDB 索引/执行计划，PostgreSQL 的 work_mem/统计信息/部分索引等）。
            请根据给出的 SQL 指纹统计与语句文本（字面量已参数化脱敏），输出 JSON（不要用 markdown 代码块包裹），结构如下：
            {"summary":"一段话总结这条 SQL 的性能问题与影响程度，结合执行次数/平均耗时/扫描行数给出量化判断",
             "causes":["慢的可能原因1（按可能性从高到低排序，最多4条，如缺索引/全表扫描/排序落盘/返回列过多等）","..."],
             "suggestions":["优化建议1（索引建议给出建议的列组合；改写建议给出改写思路；只给建议不代执行，DDL 变更提醒需评估锁表影响并在低峰窗口人工执行）","..."]}
            要求：全部使用中文；基于给出的统计与语句结构分析，没有把握的不要臆造表结构与数据分布。
            """;

    /** 上下文中匹配 IPv4 的脱敏模式（保留前两段）。 */
    private static final Pattern IPV4 = Pattern.compile("\\b(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b");
    /** SQL/文本中的单引号字面量参数化。 */
    private static final Pattern SQL_LITERAL = Pattern.compile("'[^']*'");

    @Resource
    private LlmAnalysisMapper analysisMapper;
    @Resource
    private LlmConfigServiceImpl configService;
    @Resource
    private AlertEventMapper alertEventMapper;
    @Resource
    private AlertRuleMapper alertRuleMapper;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private PasswordCipher passwordCipher;
    @Resource
    private SlowSqlQueryService slowSqlQueryService;
    @Resource
    private com.lzzh.monitor.service.instance.InstanceService instanceService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 实例的数据库类型标签（MySQL / PostgreSQL），供提示词方言分派；查询失败返回 null 不阻断分析。 */
    private String resolveDbTypeLabel(Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        try {
            var ins = instanceService.getById(instanceId);
            return ins == null ? null : ins.getDbType();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public LlmAnalysisVo getByEvent(Long eventId) {
        LlmAnalysis exist = analysisMapper.selectOne(
                new LambdaQueryWrapper<LlmAnalysis>().eq(LlmAnalysis::getEventId, eventId));
        return exist == null ? null : toVo(exist);
    }

    @Override
    public LlmAnalysisVo analyze(Long eventId, boolean regenerate, Long operatorId, String operatorName) {
        AlertEvent event = alertEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("告警事件不存在");
        }
        if (!dataScopeService.currentScope().allows(event.getInstanceId())) {
            throw new BusinessException("无权分析该实例的告警事件");
        }
        if (!regenerate) {
            LlmAnalysisVo cached = getByEvent(eventId);
            if (cached != null && Boolean.TRUE.equals(cached.getSuccess())) {
                return cached;
            }
        }

        LlmConfig config = loadEnabledConfig();

        String context = buildContext(event);
        if (!Boolean.FALSE.equals(config.getDesensitize())) {
            context = desensitize(context);
        }

        long start = System.currentTimeMillis();
        LlmAnalysis record = new LlmAnalysis();
        record.setEventId(eventId);
        record.setModel(config.getModel());
        record.setPromptChars(context.length());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setCreatedAt(OffsetDateTime.now());
        try {
            String content = callChatCompletions(config, SYSTEM_PROMPT, context);
            record.setResponseChars(content == null ? 0 : content.length());
            parseInto(record, content);
            record.setSuccess(true);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LLM 智能分析调用失败 eventId={}", eventId, e);
            record.setSuccess(false);
            record.setErrorMessage(truncate(e.getMessage(), 480));
        } finally {
            record.setDurationMs(System.currentTimeMillis() - start);
        }
        upsert(record);
        return toVo(record);
    }

    @Override
    public LlmAnalysisVo analyzeSlowSql(SlowSqlLlmAnalyzeRequest request, Long operatorId, String operatorName) {
        if (!dataScopeService.currentScope().allows(request.getInstanceId())) {
            throw new BusinessException("无权分析该实例的慢SQL");
        }
        LlmConfig config = loadEnabledConfig();

        String context = buildSlowSqlContext(request);
        if (!Boolean.FALSE.equals(config.getDesensitize())) {
            context = desensitize(context);
        }

        long start = System.currentTimeMillis();
        // 慢SQL分析按需生成不落库（窗口与样本随查询变化，缓存价值低）；操作审计由接口层记录
        LlmAnalysis record = new LlmAnalysis();
        record.setModel(config.getModel());
        record.setPromptChars(context.length());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setCreatedAt(OffsetDateTime.now());
        try {
            String content = callChatCompletions(config, SLOW_SQL_PROMPT, context);
            record.setResponseChars(content == null ? 0 : content.length());
            parseInto(record, content);
            record.setSuccess(true);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("慢SQL LLM 智能分析调用失败 instanceId={} digest={}",
                    request.getInstanceId(), request.getDigest(), e);
            record.setSuccess(false);
            record.setErrorMessage(truncate(e.getMessage(), 480));
        } finally {
            record.setDurationMs(System.currentTimeMillis() - start);
        }
        return toVo(record);
    }

    /** 校验智能分析配置：已启用 + 地址/模型完整 + 数据不出域检查，返回可用配置。 */
    private LlmConfig loadEnabledConfig() {
        LlmConfig config = configService.loadOrInit();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException("智能分析未启用，请先在「系统设置 → 智能分析设置」中配置并启用");
        }
        if (!StringUtils.hasText(config.getBaseUrl()) || !StringUtils.hasText(config.getModel())) {
            throw new BusinessException("智能分析配置不完整：接口地址与模型名称必填");
        }
        assertOutboundAllowed(config);
        return config;
    }

    // ---- 数据不出域校验 ----

    /**
     * allow_external 关闭时只放行内网/本机目标：localhost、回环、RFC1918 私网 IP、
     * 无点号的裸内网主机名、以及 .local/.internal/.lan 结尾的内网域名。
     */
    private static void assertOutboundAllowed(LlmConfig config) {
        if (Boolean.TRUE.equals(config.getAllowExternal())) {
            return;
        }
        String host;
        try {
            host = URI.create(config.getBaseUrl()).getHost();
        } catch (Exception e) {
            throw new BusinessException("接口地址格式不正确：" + config.getBaseUrl());
        }
        if (host == null || !isInternalHost(host)) {
            throw new BusinessException("数据不出域开关处于关闭状态，仅允许调用内网/本机大模型服务；"
                    + "如需调用公网服务，请在智能分析设置中显式开启「允许公网调用」");
        }
    }

    private static boolean isInternalHost(String host) {
        String lower = host.toLowerCase(java.util.Locale.ROOT);
        if ("localhost".equals(lower) || lower.endsWith(".local")
                || lower.endsWith(".internal") || lower.endsWith(".lan")) {
            return true;
        }
        if (!lower.contains(".") && !lower.contains(":")) {
            // 裸内网主机名（无域名后缀）
            return true;
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 上下文组装 ----

    private String buildContext(AlertEvent e) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("## 告警事件\n");
        appendLine(sb, "数据库类型", resolveDbTypeLabel(e.getInstanceId()));
        appendLine(sb, "规则名称", e.getRuleName());
        appendLine(sb, "告警级别", e.getRuleLevel());
        appendLine(sb, "实例名称", e.getInstanceName());
        appendLine(sb, "触发值", e.getTriggerValue());
        appendLine(sb, "阈值", e.getThresholdValue());
        appendLine(sb, "告警信息", e.getAlertMessage());
        appendLine(sb, "事件状态", e.getStatus());
        if (e.getTriggerTime() != null) {
            appendLine(sb, "触发时间", e.getTriggerTime().format(FMT));
        }
        if (e.getRecoveryTime() != null) {
            appendLine(sb, "恢复时间", e.getRecoveryTime().format(FMT));
        }
        if (e.getTriggerCount() != null && e.getTriggerCount() > 1) {
            appendLine(sb, "累计触发次数", String.valueOf(e.getTriggerCount()));
        }

        AlertRule rule = e.getRuleId() == null ? null : alertRuleMapper.selectById(e.getRuleId());
        if (rule != null) {
            appendLine(sb, "规则说明", rule.getDescription());
            appendLine(sb, "监控指标", rule.getMetricName());
        }

        // 锁相关事件：附带阻塞链现场（最多 5 行，控制 token 用量）
        Map<String, Object> chain = e.getBlockingChainSnapshot();
        if (chain != null && chain.get("rows") instanceof List<?> rows && !rows.isEmpty()) {
            sb.append("\n## 阻塞链现场（建单时抓取）\n");
            int limit = Math.min(rows.size(), 5);
            for (int i = 0; i < limit; i++) {
                sb.append("- ").append(JSONUtil.toJsonStr(rows.get(i))).append('\n');
            }
            if (rows.size() > limit) {
                sb.append("-（其余 ").append(rows.size() - limit).append(" 行省略）\n");
            }
        }
        return sb.toString();
    }

    /** 慢SQL分析上下文：窗口聚合统计（服务端重查，不信任前端传值）+ 样本 SQL。 */
    private String buildSlowSqlContext(SlowSqlLlmAnalyzeRequest req) {
        SlowSqlDigestDetailRequest dr = new SlowSqlDigestDetailRequest();
        dr.setInstanceId(req.getInstanceId());
        dr.setSchemaName(req.getSchemaName());
        dr.setDigest(req.getDigest());
        dr.setFrom(req.getFrom());
        dr.setTo(req.getTo());
        SlowSqlDigestVo detail = slowSqlQueryService.digestDetail(dr);

        StringBuilder sb = new StringBuilder(1024);
        sb.append("## 慢 SQL 指纹统计（统计窗口内）\n");
        appendLine(sb, "数据库类型", resolveDbTypeLabel(req.getInstanceId()));
        if (detail != null) {
            appendLine(sb, "库名", detail.getSchemaName());
            appendLine(sb, "SQL 类型", detail.getSqlType());
            appendNum(sb, "执行次数", detail.getExecCount());
            appendNum(sb, "总耗时(ms)", detail.getTotalTimeMs());
            appendNum(sb, "平均耗时(ms)", detail.getAvgTimeMs());
            appendNum(sb, "最大平均耗时(ms)", detail.getMaxAvgTimeMs());
            appendNum(sb, "扫描行数", detail.getRowsExamined());
            appendNum(sb, "返回行数", detail.getRowsSent());
            appendNum(sb, "扫描/返回比", detail.getScanRatio());
            appendNum(sb, "锁等待(ms)", detail.getLockTimeMs());
            appendNum(sb, "排序行数", detail.getSortRows());
            appendNum(sb, "未走索引次数", detail.getNoIndexUsed());
            appendNum(sb, "临时表次数", detail.getTmpTables());
            appendNum(sb, "磁盘临时表次数", detail.getTmpDiskTables());
        } else {
            sb.append("（统计窗口内无聚合数据，仅基于语句文本分析）\n");
        }

        String sql = StringUtils.hasText(req.getSqlText()) ? req.getSqlText()
                : detail == null ? null : detail.getDigestText();
        if (StringUtils.hasText(sql)) {
            sb.append("\n## SQL 语句\n").append(truncate(sql, 4000)).append('\n');
        }
        return sb.toString();
    }

    private static void appendNum(StringBuilder sb, String key, Number value) {
        if (value != null) {
            appendLine(sb, key, String.valueOf(value));
        }
    }

    private static void appendLine(StringBuilder sb, String key, String value) {
        if (StringUtils.hasText(value)) {
            sb.append(key).append('：').append(value).append('\n');
        }
    }

    // ---- 脱敏 ----

    /** IP 打码（保留前两段）+ SQL 单引号字面量参数化，避免业务数据/来源拓扑泄露。 */
    static String desensitize(String text) {
        String masked = IPV4.matcher(text).replaceAll("$1.$2.*.*");
        return SQL_LITERAL.matcher(masked).replaceAll("'?'");
    }

    // ---- OpenAI 兼容调用 ----

    private String callChatCompletions(LlmConfig config, String systemPrompt, String context) throws Exception {
        JSONObject body = new JSONObject();
        body.set("model", config.getModel());
        body.set("temperature", 0.3);
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
        messages.add(new JSONObject().set("role", "user").set("content", context));
        body.set("messages", messages);

        int timeout = config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0
                ? 60 : config.getTimeoutSeconds();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        String apiKey = passwordCipher.decrypt(config.getApiKey());
        if (StringUtils.hasText(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> resp = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("LLM 接口返回 HTTP " + resp.statusCode()
                    + "：" + truncate(resp.body(), 300));
        }
        JSONObject json = JSONUtil.parseObj(resp.body());
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM 接口未返回内容");
        }
        return choices.getJSONObject(0).getJSONObject("message").getStr("content");
    }

    /** 解析模型输出：容忍 markdown 代码块包裹与前后噪声，提取首个 JSON 对象。 */
    private static void parseInto(LlmAnalysis record, String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("LLM 返回内容为空");
        }
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            // 非结构化输出：整体作为总结兜底
            record.setSummary(truncate(trimmed, 2000));
            record.setCauses(List.of());
            record.setSuggestions(List.of());
            return;
        }
        JSONObject json = JSONUtil.parseObj(trimmed.substring(start, end + 1));
        record.setSummary(truncate(json.getStr("summary"), 2000));
        record.setCauses(toStringList(json.getJSONArray("causes")));
        record.setSuggestions(toStringList(json.getJSONArray("suggestions")));
    }

    private static List<String> toStringList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) {
            for (Object o : array) {
                if (o != null && StringUtils.hasText(String.valueOf(o))) {
                    result.add(truncate(String.valueOf(o), 1000));
                }
            }
        }
        return result;
    }

    // ---- 持久化 ----

    private void upsert(LlmAnalysis record) {
        LlmAnalysis exist = analysisMapper.selectOne(
                new LambdaQueryWrapper<LlmAnalysis>().eq(LlmAnalysis::getEventId, record.getEventId()));
        if (exist == null) {
            analysisMapper.insert(record);
        } else {
            record.setId(exist.getId());
            analysisMapper.updateById(record);
        }
    }

    private static LlmAnalysisVo toVo(LlmAnalysis a) {
        LlmAnalysisVo vo = new LlmAnalysisVo();
        vo.setEventId(a.getEventId());
        vo.setSuccess(a.getSuccess());
        vo.setSummary(a.getSummary());
        vo.setCauses(a.getCauses());
        vo.setSuggestions(a.getSuggestions());
        vo.setErrorMessage(a.getErrorMessage());
        vo.setModel(a.getModel());
        vo.setDurationMs(a.getDurationMs());
        vo.setOperatorName(a.getOperatorName());
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
