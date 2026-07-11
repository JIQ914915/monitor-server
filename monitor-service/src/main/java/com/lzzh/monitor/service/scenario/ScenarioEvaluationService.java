package com.lzzh.monitor.service.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.dao.entity.MonitorScenario;
import com.lzzh.monitor.dao.mapper.MetricDefinitionMapper;
import com.lzzh.monitor.dao.ts.MetricValueQueryDao;
import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator.EvalOutcome;
import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator.SignalStatus;
import com.lzzh.monitor.service.scenario.ScenarioConditionEvaluator.TriState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场景求值服务：批量取数（按指标频率分组）+ 条件组树求值 + 诊断结论渲染。
 * <p>供采集端场景评估任务与管理端"实时信号状态"接口共用，保证两侧口径一致。
 * <p>数据新鲜度按信号各自频率判断（1m 批量查询窗口 10 分钟、1h 窗口 2 小时、1d 窗口 30 天，
 * 由 {@link TsMetricLatestDao} 内置），缺失信号进入三值逻辑的 UNKNOWN 分支。
 */
@Service
public class ScenarioEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioEvaluationService.class);

    private final TsMetricLatestDao tsMetricLatestDao;
    private final MetricValueQueryDao metricValueQueryDao;
    private final MetricDefinitionMapper metricDefinitionMapper;

    public ScenarioEvaluationService(TsMetricLatestDao tsMetricLatestDao,
                                     MetricValueQueryDao metricValueQueryDao,
                                     MetricDefinitionMapper metricDefinitionMapper) {
        this.tsMetricLatestDao = tsMetricLatestDao;
        this.metricValueQueryDao = metricValueQueryDao;
        this.metricDefinitionMapper = metricDefinitionMapper;
    }

    /**
     * 场景求值结果。
     *
     * @param state     整体结果（TRUE=触发 / FALSE=正常 / UNKNOWN=数据缺失无法确认）
     * @param signals   各信号求值明细（按组树遍历顺序）
     * @param diagnosis 诊断结论：触发时按命中信号组合匹配分支结论，无匹配用默认模板；未触发为 null
     */
    public record ScenarioEvalResult(TriState state, List<SignalStatus> signals, String diagnosis) {
    }

    /** 对单个 (场景 × 实例) 求值（无实例级阈值覆盖）。 */
    public ScenarioEvalResult evaluate(MonitorScenario scenario, Long instanceId) {
        return evaluate(scenario, instanceId, null);
    }

    /**
     * 对单个 (场景 × 实例) 求值。
     *
     * @param conditionOverrides 实例级阈值覆盖（信号 code → 阈值），null/空=用模板默认阈值
     */
    public ScenarioEvalResult evaluate(MonitorScenario scenario, Long instanceId,
                                       Map<String, Object> conditionOverrides) {
        Map<String, Object> conditionConfig =
                ScenarioConditionEvaluator.applyOverrides(scenario.getConditionConfig(), conditionOverrides);
        List<Map<String, Object>> conditions = ScenarioConditionEvaluator.collectConditions(conditionConfig);
        if (conditions.isEmpty()) {
            return new ScenarioEvalResult(TriState.UNKNOWN, List.of(), null);
        }

        Map<String, String> frequencies = loadFrequencies(conditions);
        Map<String, Double> currentValues = loadCurrentValues(instanceId, conditions, frequencies);
        Map<String, Double> baselineValues = loadBaselineValues(instanceId, conditions, frequencies);

        EvalOutcome outcome = ScenarioConditionEvaluator.evaluate(conditionConfig, currentValues, baselineValues);
        String diagnosis = null;
        if (outcome.state() == TriState.TRUE) {
            diagnosis = ScenarioConditionEvaluator.resolveDiagnosis(
                    scenario.getDiagnosisBranches(), outcome.signals(), scenario.getDiagnosisTemplate());
        }
        return new ScenarioEvalResult(outcome.state(), outcome.signals(), diagnosis);
    }

    // ── 取数 ─────────────────────────────────────────────────────────────────

    private Map<String, String> loadFrequencies(List<Map<String, Object>> conditions) {
        List<String> metricCodes = conditions.stream()
                .map(c -> c.get("metricCode"))
                .filter(m -> m instanceof String s && !s.isBlank())
                .map(String.class::cast)
                .distinct()
                .toList();
        Map<String, String> result = new HashMap<>();
        if (metricCodes.isEmpty()) {
            return result;
        }
        metricDefinitionMapper.selectList(new LambdaQueryWrapper<MetricDefinition>()
                        .in(MetricDefinition::getMetricCode, metricCodes)
                        .select(MetricDefinition::getMetricCode, MetricDefinition::getFrequency))
                .forEach(md -> result.put(md.getMetricCode(),
                        md.getFrequency() == null ? "1m" : md.getFrequency()));
        return result;
    }

    /** 按频率分组批量取各指标最新值（缺失即无新鲜数据）。 */
    private Map<String, Double> loadCurrentValues(Long instanceId, List<Map<String, Object>> conditions,
                                                  Map<String, String> frequencies) {
        Map<String, Set<String>> byFreq = new HashMap<>();
        for (Map<String, Object> cond : conditions) {
            if (cond.get("metricCode") instanceof String code && !code.isBlank()) {
                String freq = frequencies.getOrDefault(code, "1m");
                byFreq.computeIfAbsent(freq, k -> new HashSet<>()).add(code);
            }
        }
        Map<String, Double> values = new HashMap<>();
        byFreq.forEach((freq, codes) -> {
            try {
                Map<String, Double> batch = switch (freq) {
                    case "1h" -> tsMetricLatestDao.latestFrom1h(instanceId, codes);
                    case "1d" -> tsMetricLatestDao.latestFrom1d(instanceId, codes);
                    default -> tsMetricLatestDao.latestFrom1m(instanceId, codes);
                };
                values.putAll(batch);
            } catch (Exception e) {
                log.warn("场景取数失败 instance={} freq={} codes={}: {}", instanceId, freq, codes, e.getMessage());
            }
        });
        return values;
    }

    /** rate_change 条件的环比基准点取数。 */
    private Map<String, Double> loadBaselineValues(Long instanceId, List<Map<String, Object>> conditions,
                                                   Map<String, String> frequencies) {
        Map<String, Double> baselines = new HashMap<>();
        List<Map<String, Object>> rateConditions = new ArrayList<>();
        for (Map<String, Object> cond : conditions) {
            if ("rate_change".equals(cond.get("condType"))) {
                rateConditions.add(cond);
            }
        }
        for (Map<String, Object> cond : rateConditions) {
            if (!(cond.get("metricCode") instanceof String code) || code.isBlank()) {
                continue;
            }
            String offset = cond.get("compareOffset") instanceof String s ? s : null;
            String key = ScenarioConditionEvaluator.baselineKey(code, offset);
            if (baselines.containsKey(key)) {
                continue;
            }
            try {
                Double base = metricValueQueryDao.valueAtOffset(instanceId, code,
                        frequencies.getOrDefault(code, "1m"),
                        ScenarioConditionEvaluator.offsetMinutes(offset));
                if (base != null) {
                    baselines.put(key, base);
                }
            } catch (Exception e) {
                log.warn("场景环比基准取数失败 instance={} metric={} offset={}: {}",
                        instanceId, code, offset, e.getMessage());
            }
        }
        return baselines;
    }
}
