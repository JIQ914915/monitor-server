package com.lzzh.monitor.service.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzzh.monitor.api.request.ScenarioDetailRequest;
import com.lzzh.monitor.api.request.ScenarioPageRequest;
import com.lzzh.monitor.api.request.ScenarioThresholdRequest;
import com.lzzh.monitor.api.request.ScenarioToggleRequest;
import com.lzzh.monitor.api.response.ScenarioPageVo;
import com.lzzh.monitor.api.response.ScenarioVo;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertEventOperateLog;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.KnowledgeArticle;
import com.lzzh.monitor.dao.entity.MonitorScenario;
import com.lzzh.monitor.dao.entity.ScenarioInstanceConfig;
import com.lzzh.monitor.dao.entity.SysUser;
import com.lzzh.monitor.dao.mapper.AlertEvaluateWindowMapper;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertEventOperateLogMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.KnowledgeArticleMapper;
import com.lzzh.monitor.dao.mapper.MonitorScenarioMapper;
import com.lzzh.monitor.dao.mapper.ScenarioInstanceConfigMapper;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.service.datascope.CurrentUserHolder;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator.SignalStatus;
import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator.TriState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 场景管理服务实现。
 * <p>列表实时计算各信号状态：复用 {@link ScenarioEvaluationService}（与采集端评估同一口径），
 * 仅针对当前实例、场景数 ≤ 10，性能可忽略。
 */
@Service
public class ScenarioServiceImpl implements ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioServiceImpl.class);

    private static final List<String> ACTIVE_EVENT_STATUSES = List.of("pending", "confirmed", "handling");
    /** 场景停用联动关闭事件后的短期抑制窗口（小时），与规则停用策略一致。 */
    private static final int SCENARIO_DISABLE_SUPPRESS_HOURS = 2;

    private final MonitorScenarioMapper scenarioMapper;
    private final ScenarioInstanceConfigMapper configMapper;
    private final DbInstanceMapper dbInstanceMapper;
    private final AlertEventMapper alertEventMapper;
    private final AlertEvaluateWindowMapper evaluateWindowMapper;
    private final AlertEventOperateLogMapper operateLogMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final SysUserMapper sysUserMapper;
    private final ScenarioEvaluationService evaluationService;
    private final DataScopeService dataScopeService;

    public ScenarioServiceImpl(MonitorScenarioMapper scenarioMapper,
                               ScenarioInstanceConfigMapper configMapper,
                               DbInstanceMapper dbInstanceMapper,
                               AlertEventMapper alertEventMapper,
                               AlertEvaluateWindowMapper evaluateWindowMapper,
                               AlertEventOperateLogMapper operateLogMapper,
                               KnowledgeArticleMapper knowledgeArticleMapper,
                               SysUserMapper sysUserMapper,
                               ScenarioEvaluationService evaluationService,
                               DataScopeService dataScopeService) {
        this.scenarioMapper = scenarioMapper;
        this.configMapper = configMapper;
        this.dbInstanceMapper = dbInstanceMapper;
        this.alertEventMapper = alertEventMapper;
        this.evaluateWindowMapper = evaluateWindowMapper;
        this.operateLogMapper = operateLogMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.sysUserMapper = sysUserMapper;
        this.evaluationService = evaluationService;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public ScenarioPageVo page(ScenarioPageRequest req) {
        DbInstance instance = requireAccessibleInstance(req.getInstanceId());
        List<MonitorScenario> scenarios = listApplicableScenarios(instance);
        Map<String, ScenarioInstanceConfig> configs = loadConfigs(instance.getId());

        // 全量统计启停数（页签角标与统计卡片），再按页签过滤 + 级别排序 + 分页
        int enabledCount = 0;
        for (MonitorScenario scenario : scenarios) {
            ScenarioInstanceConfig cfg = configs.get(scenario.getScenarioCode());
            if (cfg != null && Boolean.TRUE.equals(cfg.getEnabled())) {
                enabledCount++;
            }
        }

        List<MonitorScenario> filtered = scenarios.stream()
                .filter(s -> {
                    if (req.getEnabled() == null) {
                        return true;
                    }
                    ScenarioInstanceConfig cfg = configs.get(s.getScenarioCode());
                    boolean enabled = cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
                    return enabled == req.getEnabled();
                })
                .sorted(java.util.Comparator
                        .comparingInt((MonitorScenario s) -> severityWeight(s.getSeverity()))
                        .thenComparing(MonitorScenario::getScenarioName,
                                java.text.Collator.getInstance(java.util.Locale.CHINA)))
                .toList();

        int pageNum = req.getPageNum() == null || req.getPageNum() < 1 ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 10 : req.getPageSize();
        int fromIdx = Math.min((pageNum - 1) * pageSize, filtered.size());
        int toIdx = Math.min(fromIdx + pageSize, filtered.size());

        // 信号实时求值有指标查询开销，仅对当前页行执行
        Set<String> triggeredCodes = loadTriggeredScenarioCodes(instance.getId());
        List<ScenarioVo> vos = new ArrayList<>(toIdx - fromIdx);
        for (MonitorScenario scenario : filtered.subList(fromIdx, toIdx)) {
            ScenarioInstanceConfig cfg = configs.get(scenario.getScenarioCode());
            boolean enabled = cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
            vos.add(toVo(scenario, instance.getId(), enabled, cfg, triggeredCodes, false));
        }

        ScenarioPageVo page = new ScenarioPageVo();
        page.setTotal(scenarios.size());
        page.setFilteredTotal(filtered.size());
        page.setEnabledCount(enabledCount);
        page.setDisabledCount(scenarios.size() - enabledCount);
        page.setScenarios(vos);
        return page;
    }

    /** 级别排序权重：level_1（严重）最前，无法识别的级别排最后。 */
    private static int severityWeight(String severity) {
        if (severity != null && severity.startsWith("level_")) {
            try {
                return Integer.parseInt(severity.substring("level_".length()));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public ScenarioVo detail(ScenarioDetailRequest req) {
        DbInstance instance = requireAccessibleInstance(req.getInstanceId());
        MonitorScenario scenario = requireScenario(req.getScenarioCode());
        ScenarioInstanceConfig cfg = loadConfigs(instance.getId()).get(scenario.getScenarioCode());
        boolean enabled = cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
        Set<String> triggeredCodes = loadTriggeredScenarioCodes(instance.getId());
        return toVo(scenario, instance.getId(), enabled, cfg, triggeredCodes, true);
    }

    @Override
    @Transactional
    public boolean toggle(ScenarioToggleRequest req) {
        DbInstance instance = requireAccessibleInstance(req.getInstanceId());
        MonitorScenario scenario = requireScenario(req.getScenarioCode());
        boolean enabled = Boolean.TRUE.equals(req.getEnabled());

        ScenarioInstanceConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<ScenarioInstanceConfig>()
                .eq(ScenarioInstanceConfig::getScenarioCode, scenario.getScenarioCode())
                .eq(ScenarioInstanceConfig::getInstanceId, instance.getId())
                .last("LIMIT 1"));
        OffsetDateTime now = OffsetDateTime.now();
        if (cfg == null) {
            cfg = new ScenarioInstanceConfig();
            cfg.setScenarioCode(scenario.getScenarioCode());
            cfg.setInstanceId(instance.getId());
            cfg.setEnabled(enabled);
            cfg.setTriggerCount(0L);
            cfg.setCreatedAt(now);
            cfg.setUpdatedAt(now);
            configMapper.insert(cfg);
        } else {
            cfg.setEnabled(enabled);
            cfg.setUpdatedAt(now);
            configMapper.updateById(cfg);
        }
        if (!enabled) {
            // 停用联动关闭该场景在此实例的活跃综合事件（同规则停用的止噪语义）
            closeActiveEventsByScenario(scenario.getScenarioCode(), instance.getId());
        }
        log.info("场景启停 scenario=[{}] instance=[{}] enabled={}", scenario.getScenarioCode(), instance.getId(), enabled);
        return true;
    }

    @Override
    @Transactional
    public boolean updateThresholds(ScenarioThresholdRequest req) {
        DbInstance instance = requireAccessibleInstance(req.getInstanceId());
        MonitorScenario scenario = requireScenario(req.getScenarioCode());

        Map<String, Object> overrides = validateOverrides(scenario, req.getOverrides());

        ScenarioInstanceConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<ScenarioInstanceConfig>()
                .eq(ScenarioInstanceConfig::getScenarioCode, scenario.getScenarioCode())
                .eq(ScenarioInstanceConfig::getInstanceId, instance.getId())
                .last("LIMIT 1"));
        OffsetDateTime now = OffsetDateTime.now();
        if (cfg == null) {
            cfg = new ScenarioInstanceConfig();
            cfg.setScenarioCode(scenario.getScenarioCode());
            cfg.setInstanceId(instance.getId());
            cfg.setEnabled(false);
            cfg.setTriggerCount(0L);
            cfg.setConditionOverrides(overrides);
            cfg.setCreatedAt(now);
            cfg.setUpdatedAt(now);
            configMapper.insert(cfg);
        } else {
            cfg.setConditionOverrides(overrides);
            cfg.setUpdatedAt(now);
            configMapper.updateById(cfg);
        }
        log.info("场景阈值调整 scenario=[{}] instance=[{}] overrides={}",
                scenario.getScenarioCode(), instance.getId(), overrides);
        return true;
    }

    @Override
    @Transactional
    public int enableRecommended(Long instanceId) {
        DbInstance instance = requireAccessibleInstance(instanceId);
        List<MonitorScenario> applicable = listApplicableScenarios(instance);
        Map<String, ScenarioInstanceConfig> configs = loadConfigs(instance.getId());
        OffsetDateTime now = OffsetDateTime.now();
        int enabledNow = 0;
        for (MonitorScenario scenario : applicable) {
            if (!Boolean.TRUE.equals(scenario.getRecommended())) {
                continue;
            }
            // 主机类场景依赖 host.* 指标：未关联主机的实例开了也全是数据缺失，跳过
            if (scenario.getScenarioCode() != null
                    && scenario.getScenarioCode().startsWith("scenario.host_")
                    && instance.getHostId() == null) {
                continue;
            }
            ScenarioInstanceConfig cfg = configs.get(scenario.getScenarioCode());
            if (cfg != null && Boolean.TRUE.equals(cfg.getEnabled())) {
                continue;
            }
            if (cfg == null) {
                cfg = new ScenarioInstanceConfig();
                cfg.setScenarioCode(scenario.getScenarioCode());
                cfg.setInstanceId(instance.getId());
                cfg.setEnabled(true);
                cfg.setTriggerCount(0L);
                cfg.setCreatedAt(now);
                cfg.setUpdatedAt(now);
                configMapper.insert(cfg);
            } else {
                cfg.setEnabled(true);
                cfg.setUpdatedAt(now);
                configMapper.updateById(cfg);
            }
            enabledNow++;
        }
        log.info("一键开启常用场景 instance=[{}] 新开启 {} 个", instance.getId(), enabledNow);
        return enabledNow;
    }

    /** 校验覆盖项：信号 code 必须存在于模板条件树中且阈值为有限数值；全部与默认一致/为空则存 null（回归默认）。 */
    private Map<String, Object> validateOverrides(MonitorScenario scenario, Map<String, Double> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        Map<String, Map<String, Object>> templateByCode = templateConditionsByCode(scenario);
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Double> entry : overrides.entrySet()) {
            Map<String, Object> template = templateByCode.get(entry.getKey());
            if (template == null) {
                throw new IllegalArgumentException("信号不存在：" + entry.getKey());
            }
            Double value = entry.getValue();
            if (value == null || value.isNaN() || value.isInfinite()) {
                throw new IllegalArgumentException("信号 " + entry.getKey() + " 阈值无效");
            }
            // 与模板默认值相同的覆盖不落库，保持"未覆盖即跟随模板"语义
            Object defaultThreshold = template.get("threshold");
            if (defaultThreshold instanceof Number n && Double.compare(n.doubleValue(), value) == 0) {
                continue;
            }
            cleaned.put(entry.getKey(), value);
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private Map<String, Map<String, Object>> templateConditionsByCode(MonitorScenario scenario) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> cond : ScenarioConditionEvaluator.collectConditions(scenario.getConditionConfig())) {
            if (cond.get("code") instanceof String code && !code.isBlank()) {
                result.put(code, cond);
            }
        }
        return result;
    }

    // ── 内部实现 ─────────────────────────────────────────────────────────────

    private DbInstance requireAccessibleInstance(Long instanceId) {
        DbInstance instance = dbInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("实例不存在：" + instanceId);
        }
        if (!dataScopeService.currentScope().allows(instanceId)) {
            throw new IllegalArgumentException("无权访问该实例");
        }
        return instance;
    }

    private MonitorScenario requireScenario(String scenarioCode) {
        MonitorScenario scenario = scenarioMapper.selectOne(new LambdaQueryWrapper<MonitorScenario>()
                .eq(MonitorScenario::getScenarioCode, scenarioCode)
                .last("LIMIT 1"));
        if (scenario == null) {
            throw new IllegalArgumentException("场景不存在：" + scenarioCode);
        }
        return scenario;
    }

    /** 按实例的 db_type/db_version 过滤适配场景（db_version_ids 为空 = 全版本适用）。 */
    private List<MonitorScenario> listApplicableScenarios(DbInstance instance) {
        return scenarioMapper.selectList(null).stream()
                .filter(s -> s.getDbTypeId() == null || s.getDbTypeId().equals(instance.getDbTypeId()))
                .filter(s -> s.getDbVersionIds() == null || s.getDbVersionIds().isEmpty()
                        || (instance.getDbVersionId() != null && s.getDbVersionIds().contains(instance.getDbVersionId())))
                .toList();
    }

    private Map<String, ScenarioInstanceConfig> loadConfigs(Long instanceId) {
        return configMapper.selectList(new LambdaQueryWrapper<ScenarioInstanceConfig>()
                        .eq(ScenarioInstanceConfig::getInstanceId, instanceId))
                .stream()
                .collect(Collectors.toMap(ScenarioInstanceConfig::getScenarioCode, c -> c, (a, b) -> a));
    }

    /** 当前实例存在活跃综合事件的场景编码集合（触发中状态判定）。 */
    private Set<String> loadTriggeredScenarioCodes(Long instanceId) {
        return alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                        .select(AlertEvent::getScenarioCode)
                        .eq(AlertEvent::getInstanceId, instanceId)
                        .eq(AlertEvent::getEventSource, "scenario")
                        .in(AlertEvent::getStatus, ACTIVE_EVENT_STATUSES))
                .stream()
                .map(AlertEvent::getScenarioCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private ScenarioVo toVo(MonitorScenario scenario, Long instanceId, boolean enabled,
                            ScenarioInstanceConfig cfg, Set<String> triggeredCodes, boolean withDetail) {
        ScenarioVo vo = new ScenarioVo();
        vo.setId(scenario.getId());
        vo.setScenarioCode(scenario.getScenarioCode());
        vo.setScenarioName(scenario.getScenarioName());
        vo.setDescription(scenario.getDescription());
        vo.setSeverity(scenario.getSeverity());
        vo.setBuiltin(scenario.getBuiltin());
        vo.setRecommended(Boolean.TRUE.equals(scenario.getRecommended()));
        vo.setEnabled(enabled);
        vo.setTriggerCount(cfg == null || cfg.getTriggerCount() == null ? 0L : cfg.getTriggerCount());
        Map<String, Object> conditionConfig = scenario.getConditionConfig();
        if (conditionConfig != null) {
            vo.setLogic(String.valueOf(conditionConfig.getOrDefault("logic", "AND")));
            Object duration = conditionConfig.get("duration");
            vo.setDuration(duration instanceof Number n ? n.intValue() : 0);
        }

        // 实时求值各信号状态（与采集端同一口径，含实例级阈值覆盖）
        Map<String, Object> overrides = cfg == null ? null : cfg.getConditionOverrides();
        ScenarioEvaluationService.ScenarioEvalResult result =
                evaluationService.evaluate(scenario, instanceId, overrides);
        vo.setSignals(toSignalMaps(result.signals(), scenario, overrides));

        boolean triggered = triggeredCodes.contains(scenario.getScenarioCode());
        if (!enabled) {
            vo.setCurrentStatus("disabled");
        } else if (triggered) {
            vo.setCurrentStatus("triggered");
        } else if (result.state() == TriState.UNKNOWN) {
            vo.setCurrentStatus("unknown");
        } else {
            vo.setCurrentStatus("normal");
        }

        if (withDetail) {
            // 仅触发中返回渲染后的诊断结论；模板单独下发供未触发时预览，避免误读为当前诊断
            vo.setDiagnosis(triggered ? result.diagnosis() : null);
            vo.setDiagnosisTemplate(scenario.getDiagnosisTemplate());
            vo.setKnowledgeArticles(loadKnowledgeArticles(scenario.getKnowledgeArticleIds()));
        }
        return vo;
    }

    private List<Map<String, Object>> toSignalMaps(List<SignalStatus> signals, MonitorScenario scenario,
                                                   Map<String, Object> overrides) {
        Map<String, Map<String, Object>> templateByCode = templateConditionsByCode(scenario);
        List<Map<String, Object>> result = new ArrayList<>(signals.size());
        for (SignalStatus s : signals) {
            Map<String, Object> item = new HashMap<>();
            item.put("code", s.code());
            item.put("name", s.name());
            item.put("expr", s.exprText());
            item.put("metricCode", s.metricCode());
            item.put("currentVal", formatSignalValue(s.currentValue(), s.unit()));
            item.put("met", s.state() == TriState.TRUE);
            item.put("state", switch (s.state()) {
                case TRUE -> "met";
                case FALSE -> "normal";
                case UNKNOWN -> "unknown";
            });
            // 阈值编辑元数据：模板默认值 + 实例级生效值（供前端"调整阈值"表单）
            Map<String, Object> template = s.code() == null ? null : templateByCode.get(s.code());
            if (template != null) {
                item.put("condType", template.get("condType"));
                item.put("operator", template.get("operator"));
                item.put("unit", template.get("unit"));
                Object defaultThreshold = template.get("threshold");
                item.put("defaultThreshold", defaultThreshold);
                Object effective = overrides == null ? null : overrides.get(s.code());
                item.put("threshold", effective instanceof Number ? effective : defaultThreshold);
            }
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> loadKnowledgeArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeArticle> byId = knowledgeArticleMapper.selectByIds(articleIds).stream()
                .collect(Collectors.toMap(KnowledgeArticle::getId, a -> a, (a, b) -> a));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : articleIds) {
            KnowledgeArticle article = byId.get(id);
            if (article != null) {
                result.add(Map.of("id", article.getId(),
                        "title", article.getTitle(),
                        "category", article.getCategory() == null ? "" : article.getCategory()));
            }
        }
        return result;
    }

    private static String formatSignalValue(Double value, String unit) {
        if (value == null) {
            return "-";
        }
        double rounded = Math.round(value * 100.0) / 100.0;
        String text = rounded == Math.floor(rounded) && !Double.isInfinite(rounded)
                ? String.valueOf((long) rounded)
                : String.valueOf(rounded);
        return unit == null || unit.isBlank() ? text : text + unit;
    }

    /** 场景停用联动关闭活跃综合事件 + 清理持续窗口（对齐规则停用 closeActiveEventsByRule 语义）。 */
    private void closeActiveEventsByScenario(String scenarioCode, Long instanceId) {
        OffsetDateTime now = OffsetDateTime.now();
        String dedupKey = scenarioCode + ":" + instanceId;
        // 清理持续窗口（含尚未生成事件的 pending 窗口），避免重新启用后绕过持续时长判定
        evaluateWindowMapper.deleteByRuleInstance(dedupKey, dedupKey + ":");
        List<AlertEvent> affected = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .select(AlertEvent::getId, AlertEvent::getEventCode, AlertEvent::getStatus)
                .eq(AlertEvent::getDedupKey, dedupKey)
                .in(AlertEvent::getStatus, ACTIVE_EVENT_STATUSES));
        if (affected.isEmpty()) {
            return;
        }
        Long operatorId = currentUserId();
        String operatorName = resolveOperatorName(operatorId);
        String remark = "场景停用，系统联动关闭关联综合事件";
        for (AlertEvent e : affected) {
            // 逐事件带 status 守卫（同规则停用）：评估线程可能已将事件置为 recovered
            int updated = alertEventMapper.update(null, new LambdaUpdateWrapper<AlertEvent>()
                    .eq(AlertEvent::getId, e.getId())
                    .in(AlertEvent::getStatus, ACTIVE_EVENT_STATUSES)
                    .set(AlertEvent::getStatus, "closed")
                    .set(AlertEvent::getCloseUserId, operatorId)
                    .set(AlertEvent::getSilenceUntilTime, now.plusHours(SCENARIO_DISABLE_SUPPRESS_HOURS))
                    .set(AlertEvent::getLastRemark, remark)
                    .set(AlertEvent::getUpdatedAt, now));
            if (updated == 0) {
                continue;
            }
            AlertEventOperateLog opLog = new AlertEventOperateLog();
            opLog.setEventId(e.getId());
            opLog.setEventCode(e.getEventCode());
            opLog.setOperateType("close");
            opLog.setFromStatus(e.getStatus());
            opLog.setToStatus("closed");
            opLog.setOperatorId(operatorId);
            opLog.setOperatorName(operatorName);
            opLog.setRemark(remark);
            opLog.setCreatedAt(now);
            operateLogMapper.insert(opLog);
        }
    }

    private Long currentUserId() {
        CurrentUserHolder.Current current = CurrentUserHolder.get();
        return current == null ? null : current.userId();
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) {
            return "系统";
        }
        SysUser user = sysUserMapper.selectById(operatorId);
        if (user == null) {
            return "系统";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
