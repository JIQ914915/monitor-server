package com.lzzh.monitor.service.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.AlertRulePageRequest;
import com.lzzh.monitor.api.request.AlertRuleSaveRequest;
import com.lzzh.monitor.api.request.BuiltinRulePageRequest;
import com.lzzh.monitor.api.request.BuiltinRuleSaveRequest;
import com.lzzh.monitor.api.response.AlertRuleVo;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertEventOperateLog;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.dao.entity.AlertRuleMetricRef;
import com.lzzh.monitor.dao.entity.AlertRuleInstanceConfig;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.SysUser;
import com.lzzh.monitor.dao.mapper.AlertEvaluateWindowMapper;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertEventOperateLogMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleMetricRefMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleInstanceConfigMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleMapper;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.MetricDefinitionMapper;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.service.datascope.CurrentUserHolder;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadata;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadataService;
import jakarta.annotation.Resource;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 告警规则服务实现。
 *
 * <p>规则查询逻辑（以 instanceId 为上下文）：
 * <ul>
 *   <li>alert_rule 仅存内置模板（按 dbTypeId/dbVersionIds 过滤适配实例）。</li>
 *   <li>自定义规则直接存 alert_rule_instance_config（rule_type=custom + instance_id）。</li>
 *   <li>内置规则启停与参数：全部写 alert_rule_instance_config；模板表默认停用。</li>
 *   <li>自定义规则启停：修改 alert_rule_instance_config.enabled。</li>
 * </ul>
 */
@Service
public class AlertRuleServiceImpl implements AlertRuleService {
    private static final Logger log = LoggerFactory.getLogger(AlertRuleServiceImpl.class);
    private static final String DEFAULT_MULTI_DISPLAY_TEMPLATE = "监控对象{dimensionKey}当前值{triggerValue}，超过阈值{thresholdValue}";
    private static final int SYSTEM_MIN_INTERVAL_MINUTES = 1;
    private static final boolean ENFORCE_INTERVAL_MULTIPLE = true;
    private static final List<String> ACTIVE_EVENT_STATUSES = List.of("pending", "confirmed", "handling");
    /** 规则停用/删除联动关闭事件后的短期抑制窗口（小时），与人工关闭的抑制策略保持一致。 */
    private static final int RULE_DISABLE_SUPPRESS_HOURS = 2;

    @Resource
    private AlertRuleMapper alertRuleMapper;
    @Resource
    private AlertEventMapper alertEventMapper;
    @Resource
    private AlertRuleMetricRefMapper metricRefMapper;
    @Resource
    private AlertRuleInstanceConfigMapper instanceConfigMapper;
    @Resource
    private AlertEventOperateLogMapper operateLogMapper;
    @Resource
    private AlertEvaluateWindowMapper evaluateWindowMapper;
    @Resource
    private DbInstanceMapper dbInstanceMapper;
    @Resource
    private DatabaseTypeMapper databaseTypeMapper;
    @Resource
    private DatabaseVersionMapper databaseVersionMapper;
    @Resource
    private MetricDefinitionMapper metricDefinitionMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private InstanceRuntimeMetadataService runtimeMetadataService;

    /** 校验实例是否在当前用户数据范围内，越权配置统一拒绝（403 语义）。 */
    private void checkInstanceAccessible(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        if (!dataScopeService.currentScope().allows(instanceId)) {
            throw new BusinessException("无权操作该实例的告警规则: " + instanceId);
        }
    }

    /** 根据服务端数据库类型表解析受支持类型，禁止相信请求中的类型文本。 */
    private DbType requireDbType(Long dbTypeId) {
        if (dbTypeId == null) {
            throw new IllegalArgumentException("适用数据库类型不能为空");
        }
        DatabaseType row = databaseTypeMapper.selectById(dbTypeId);
        DbType type = DbType.of(row == null ? null : row.getCode());
        if (type == null) {
            throw new IllegalArgumentException("数据库类型不存在或暂不支持: " + dbTypeId);
        }
        return type;
    }

    /** 单实例规则只根据 instanceId 解析真实类型，不接收前端传入的 dbType。 */
    private DbType requireInstanceDbType(Long instanceId) {
        DbType type = DbType.of(runtimeMetadataService.getRequired(instanceId).dbTypeCode());
        if (type == null) {
            throw new IllegalArgumentException("实例数据库类型暂不支持：" + instanceId);
        }
        return type;
    }
    // ── 分页查询 ─────────────────────────────────────────────────────────────

    @Override
    public PageResult<AlertRuleVo> page(AlertRulePageRequest req) {
        Long instanceId = req.getInstanceId();

        if (instanceId == null) {
            // 无实例上下文：管理员视角，返回全量规则（不含 instanceEnabled）
            return pageGlobal(req);
        }
        checkInstanceAccessible(instanceId);

        // 类型和版本统一从运行元数据缓存解析；实例实体仅用于主机关联判断
        InstanceRuntimeMetadata metadata = runtimeMetadataService.getRequired(instanceId);
        Long instDbTypeId = metadata.dbTypeId();
        Long instDbVersionId = metadata.dbVersionId();
        DbInstance inst = dbInstanceMapper.selectById(instanceId);
        Long instHostId = inst == null ? null : inst.getHostId();

        // 查询适用于本实例的内置规则（按 dbTypeId + dbVersionIds；关联主机的实例追加 HOST 主机规则）
        List<AlertRule> builtins = queryBuiltins(instDbTypeId, instDbVersionId);
        if (instHostId != null) {
            builtins = appendHostBuiltins(builtins);
        }

        // 查询该实例的自定义规则（已迁移到实例配置表）；启停状态由配置表承载，单独建映射
        List<AlertRuleInstanceConfig> customCfgs = loadCustomConfigsByInstance(instanceId);
        Map<String, Boolean> customEnabledMap = customCfgs.stream()
                .collect(Collectors.toMap(
                        AlertRuleInstanceConfig::getRuleCode,
                        c -> Boolean.TRUE.equals(c.getEnabled()),
                        (a, b) -> a));
        List<AlertRule> customs = customCfgs.stream()
                .map(this::materializeCustomRule)
                .toList();

        // 合并（内置在前，自定义在后；分页先不精确，在内存中 slice）
        List<AlertRule> all = new ArrayList<>(builtins);
        all.addAll(customs);

        // 过滤（keyword / category / ruleLevel / enabled 在 instanceEnabled 维度）
        Map<String, AlertRuleInstanceConfig> overrideMap =
                loadOverrideConfigs(instanceId, builtins.stream().map(AlertRule::getRuleCode).toList());

        List<AlertRule> filtered = all.stream().filter(r -> {
            if (StringUtils.hasText(req.getKeyword())
                    && !r.getRuleName().contains(req.getKeyword())) {
                return false;
            }
            if (StringUtils.hasText(req.getRuleLevel())
                    && !req.getRuleLevel().equals(r.getRuleLevel())) {
                return false;
            }
            if (req.getEnabled() != null) {
                boolean instEnabled = resolveInstanceEnabled(r, overrideMap, customEnabledMap);
                if (!req.getEnabled().equals(instEnabled)) {
                    return false;
                }
            }
            return true;
        }).toList();

        // 默认排序：自定义启用 → 内置启用 → 自定义停用 → 内置停用；
        // 组内保持原有次序（内置按级别升序、自定义按创建时间倒序），sorted 为稳定排序
        filtered = filtered.stream()
                .sorted(Comparator.comparingInt(r -> {
                    boolean enabled = resolveInstanceEnabled(r, overrideMap, customEnabledMap);
                    boolean builtin = "builtin".equals(r.getRuleType());
                    return (enabled ? 0 : 2) + (builtin ? 1 : 0);
                }))
                .toList();

        // 内存分页
        long total = filtered.size();
        int from = (int) ((req.getPageNum() - 1) * req.getPageSize());
        int to   = (int) Math.min(from + req.getPageSize(), total);
        List<AlertRule> paged = (from >= total) ? Collections.emptyList() : filtered.subList(from, to);

        // 聚合触发统计（当前实例维度）
        Map<Long, Integer> triggerCountMap = new HashMap<>();
        Map<Long, OffsetDateTime> lastTriggerMap = new HashMap<>();
        List<Long> ruleIds = paged.stream().map(AlertRule::getId).toList();
        if (!ruleIds.isEmpty()) {
            alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                    .in(AlertEvent::getRuleId, ruleIds)
                    .eq(AlertEvent::getInstanceId, instanceId)
                    .select(AlertEvent::getRuleId, AlertEvent::getTriggerCount, AlertEvent::getLastTriggerTime)
            ).forEach(e -> {
                triggerCountMap.merge(e.getRuleId(),
                        e.getTriggerCount() == null ? 0 : e.getTriggerCount(), Integer::sum);
                if (e.getLastTriggerTime() != null) {
                    lastTriggerMap.merge(e.getRuleId(), e.getLastTriggerTime(),
                            (a, b) -> a.isAfter(b) ? a : b);
                }
            });
        }

        // 预加载 dbTypeId → label、dbVersionId → versionCode 映射（避免 N+1 查询）
        Function<Long, String> typeNameFn = buildTypeNameFn();
        Function<Long, String> versionCodeFn = buildVersionCodeFn();
        Map<Long, List<String>> metricCodesByRule = loadMetricCodesByRule(ruleIds);

        List<AlertRuleVo> vos = paged.stream()
                .map(r -> toVo(r, triggerCountMap.getOrDefault(r.getId(), 0),
                        lastTriggerMap.get(r.getId()),
                        resolveInstanceEnabled(r, overrideMap, customEnabledMap),
                        overrideMap.get(r.getRuleCode()),
                        typeNameFn, versionCodeFn,
                        metricCodesByRule.getOrDefault(r.getId(), Collections.emptyList())))
                .toList();

        return PageResult.of(vos, total);
    }

    /**
     * 无实例上下文的全量分页（管理员用，不携带 instanceEnabled）。
     * <p>启停状态是实例级概念（alert_rule_instance_config.enabled），模板表已无 enabled 列，
     * 全局视角下 {@code req.enabled} 过滤条件无意义，直接忽略。
     */
    private PageResult<AlertRuleVo> pageGlobal(AlertRulePageRequest req) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<AlertRule>()
                .like(StringUtils.hasText(req.getKeyword()), AlertRule::getRuleName, req.getKeyword())
                .eq(StringUtils.hasText(req.getRuleLevel()), AlertRule::getRuleLevel, req.getRuleLevel())
                .orderByDesc(AlertRule::getCreatedAt);

        Page<AlertRule> page = alertRuleMapper.selectPage(
                new Page<>(req.getPageNum(), req.getPageSize()), wrapper);

        Function<Long, String> typeNameFn = buildTypeNameFn();
        Function<Long, String> versionCodeFn = buildVersionCodeFn();
        List<Long> ruleIds = page.getRecords().stream().map(AlertRule::getId).toList();
        Map<Long, List<String>> metricCodesByRule = loadMetricCodesByRule(ruleIds);
        List<AlertRuleVo> vos = page.getRecords().stream()
                .map(r -> {
                    r.setRuleType("builtin");
                    return toVo(r, 0, null, false, null, typeNameFn, versionCodeFn,
                            metricCodesByRule.getOrDefault(r.getId(), Collections.emptyList()));
                })
                .toList();
        return PageResult.of(vos, page.getTotal());
    }

    // ── 单条查询 ─────────────────────────────────────────────────────────────

    @Override
    public AlertRuleVo getByRuleCode(String ruleCode) {
        AlertRule rule = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getRuleCode, ruleCode)
                .last("LIMIT 1"));
        if (rule != null) {
            rule.setRuleType("builtin");
            List<String> metricCodes = loadMetricCodesByRule(List.of(rule.getId()))
                    .getOrDefault(rule.getId(), Collections.emptyList());
            return toVo(rule, 0, null, false,
                    null, buildTypeNameFn(), buildVersionCodeFn(), metricCodes);
        }
        AlertRuleInstanceConfig customCfg = loadCustomConfigByRuleCode(ruleCode);
        if (customCfg != null) {
            // 自定义规则绑定具体实例：详情查询同样受数据范围约束，避免知晓 ruleCode 即可越权读取
            // 他人实例的规则配置（含 customSql）。
            checkInstanceAccessible(customCfg.getInstanceId());
            AlertRule customRule = materializeCustomRule(customCfg);
            return toVo(customRule, 0, null, Boolean.TRUE.equals(customCfg.getEnabled()), customCfg,
                    buildTypeNameFn(), buildVersionCodeFn(), loadCustomMetricCodes(customCfg));
        }
        throw new IllegalArgumentException("规则不存在：" + ruleCode);
    }

    // ── 保存 ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AlertRuleVo save(AlertRuleSaveRequest req) {
        String incomingRuleCode = StringUtils.hasText(req.getRuleCode()) ? req.getRuleCode().trim() : null;
        AlertRule builtinTemplate = null;
        if (StringUtils.hasText(incomingRuleCode) && !incomingRuleCode.startsWith("custom.")) {
            builtinTemplate = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRule>()
                    .eq(AlertRule::getRuleCode, incomingRuleCode)
                    .last("LIMIT 1"));
        } else if (req.getId() != null) {
            builtinTemplate = alertRuleMapper.selectById(req.getId());
        }
        if (builtinTemplate != null) {
            builtinTemplate.setRuleType("builtin");
            if (req.getInstanceId() == null) {
                throw new IllegalArgumentException("编辑内置规则必须传 instanceId");
            }
            checkInstanceAccessible(req.getInstanceId());
            validateBuiltinRuleNameImmutable(builtinTemplate, req);
            // 布尔型锁定规则：条件由系统固化，剥离请求中的条件类字段，
            // 实例侧只允许覆盖告警级别、扫描间隔、通知设置与启停
            if (isConditionLocked(builtinTemplate.getConditionConfig())) {
                stripConditionFields(req);
            }
            // 停用为软停用（enabled=false），保留用户已覆盖的阈值/通道等配置，重新启用后不丢失
            AlertRuleInstanceConfig cfg = saveBuiltinInstanceConfig(builtinTemplate, req);
            if (Boolean.FALSE.equals(req.getEnabled())) {
                closeActiveEventsByRule(builtinTemplate.getRuleCode(), req.getInstanceId());
            }
            List<String> metricCodes = loadMetricCodesByRule(List.of(builtinTemplate.getId()))
                    .getOrDefault(builtinTemplate.getId(), Collections.emptyList());
            return toVo(builtinTemplate, 0, null, Boolean.TRUE.equals(cfg.getEnabled()), cfg,
                    buildTypeNameFn(), buildVersionCodeFn(), metricCodes);
        }

        if (req.getInstanceId() == null) {
            throw new IllegalArgumentException("自定义规则必须传 instanceId");
        }
        checkInstanceAccessible(req.getInstanceId());
        AlertRuleInstanceConfig customCfg = req.getId() == null
                ? loadCustomConfigByRuleCode(incomingRuleCode)
                : loadCustomConfigById(req.getId());
        if (customCfg == null && StringUtils.hasText(incomingRuleCode)) {
            customCfg = loadCustomConfigByRuleCode(incomingRuleCode);
        }
        DbType instanceDbType = requireInstanceDbType(req.getInstanceId());
        customCfg = saveCustomInstanceConfig(customCfg, req, instanceDbType);
        // 与内置规则 save、toggleEnabled 停用路径保持一致：停用即联动关闭活跃事件并清理持续窗口
        if (Boolean.FALSE.equals(customCfg.getEnabled())) {
            closeActiveEventsByRule(customCfg.getRuleCode(), customCfg.getInstanceId());
        }
        AlertRule customRule = materializeCustomRule(customCfg);
        return toVo(customRule, 0, null, Boolean.TRUE.equals(customCfg.getEnabled()), customCfg,
                buildTypeNameFn(), buildVersionCodeFn(), loadCustomMetricCodes(customCfg));
    }

    // ── 启停 ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void toggleEnabled(String ruleCode, boolean enabled, Long instanceId) {
        AlertRule rule = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getRuleCode, ruleCode)
                .last("LIMIT 1"));
        if (rule != null) {
            rule.setRuleType("builtin");
            if (instanceId == null) {
                throw new IllegalArgumentException("内置规则启停必须传 instanceId");
            }
            checkInstanceAccessible(instanceId);
            if (enabled) {
                AlertRuleInstanceConfig cfg = ensureBuiltinInstanceConfig(rule.getRuleCode(), instanceId, true);
                cfg.setRuleName(rule.getRuleName());
                if (cfg.getScanIntervalMin() == null) {
                    cfg.setScanIntervalMin(rule.getScanIntervalMin());
                }
                cfg.setUpdatedAt(OffsetDateTime.now());
                instanceConfigMapper.updateById(cfg);
            } else {
                // 软停用：保留用户已覆盖的阈值/通道等配置；无配置行本就处于停用态，无需新建
                AlertRuleInstanceConfig cfg = instanceConfigMapper.selectOne(
                        new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                                .eq(AlertRuleInstanceConfig::getRuleCode, rule.getRuleCode())
                                .eq(AlertRuleInstanceConfig::getInstanceId, instanceId)
                                .eq(AlertRuleInstanceConfig::getRuleType, "builtin")
                                .last("LIMIT 1"));
                if (cfg != null) {
                    cfg.setEnabled(false);
                    cfg.setUpdatedAt(OffsetDateTime.now());
                    instanceConfigMapper.updateById(cfg);
                }
                closeActiveEventsByRule(rule.getRuleCode(), instanceId);
            }
            return;
        }
        AlertRuleInstanceConfig customCfg = loadCustomConfigByRuleCode(ruleCode);
        if (customCfg == null) {
            throw new IllegalArgumentException("规则不存在：" + ruleCode);
        }
        checkInstanceAccessible(customCfg.getInstanceId());
        customCfg.setEnabled(enabled);
        customCfg.setUpdatedAt(OffsetDateTime.now());
        instanceConfigMapper.updateById(customCfg);
        if (!enabled) {
            closeActiveEventsByRule(customCfg.getRuleCode(), customCfg.getInstanceId());
        }
    }

    @Override
    @Transactional
    public int enableRecommended(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("一键开启常用规则必须传 instanceId");
        }
        checkInstanceAccessible(instanceId);
        InstanceRuntimeMetadata metadata = runtimeMetadataService.getRequired(instanceId);
        DbInstance inst = dbInstanceMapper.selectById(instanceId);
        // 与规则列表同一套适配口径：类型 + 版本过滤，关联主机的实例追加 HOST 规则
        List<AlertRule> builtins = queryBuiltins(metadata.dbTypeId(), metadata.dbVersionId());
        if (inst != null && inst.getHostId() != null) {
            builtins = appendHostBuiltins(builtins);
        }
        int enabledNow = 0;
        for (AlertRule rule : builtins) {
            if (!Boolean.TRUE.equals(rule.getRecommended())) {
                continue;
            }
            AlertRuleInstanceConfig existing = instanceConfigMapper.selectOne(
                    new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                            .eq(AlertRuleInstanceConfig::getRuleCode, rule.getRuleCode())
                            .eq(AlertRuleInstanceConfig::getInstanceId, instanceId)
                            .eq(AlertRuleInstanceConfig::getRuleType, "builtin")
                            .last("LIMIT 1"));
            if (existing != null && Boolean.TRUE.equals(existing.getEnabled())) {
                continue;
            }
            // 复用单条启用路径的语义：建/改实例配置行，保留已有阈值/通知覆盖
            AlertRuleInstanceConfig cfg = ensureBuiltinInstanceConfig(rule.getRuleCode(), instanceId, true);
            cfg.setRuleName(rule.getRuleName());
            if (cfg.getScanIntervalMin() == null) {
                cfg.setScanIntervalMin(rule.getScanIntervalMin());
            }
            cfg.setUpdatedAt(OffsetDateTime.now());
            instanceConfigMapper.updateById(cfg);
            enabledNow++;
        }
        log.info("一键开启常用规则 instance=[{}] 新开启 {} 条", instanceId, enabledNow);
        return enabledNow;
    }

    // ── 删除 ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(String ruleCode) {
        AlertRule rule = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getRuleCode, ruleCode)
                .last("LIMIT 1"));
        if (rule != null) {
            throw new IllegalStateException("内置规则不允许删除");
        }
        AlertRuleInstanceConfig customCfg = loadCustomConfigByRuleCode(ruleCode);
        if (customCfg == null) {
            throw new IllegalArgumentException("规则不存在：" + ruleCode);
        }
        checkInstanceAccessible(customCfg.getInstanceId());
        closeActiveEventsByRule(customCfg.getRuleCode(), customCfg.getInstanceId());
        instanceConfigMapper.deleteById(customCfg.getId());
    }

    @Override
    @Transactional
    public void updateScanInterval(String ruleCode, int scanIntervalMin, Long instanceId) {
        AlertRule rule = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getRuleCode, ruleCode)
                .last("LIMIT 1"));
        if (rule != null) {
            List<String> metricCodes = loadMetricCodesByRule(List.of(rule.getId()))
                    .getOrDefault(rule.getId(), Collections.emptyList());
            IntervalCapability capability = computeIntervalCapability(metricCodes);
            validateScanInterval(scanIntervalMin, capability);
            if (instanceId == null) {
                throw new IllegalArgumentException("内置规则调频必须传 instanceId");
            }
            checkInstanceAccessible(instanceId);
            AlertRuleInstanceConfig cfg = ensureBuiltinInstanceConfig(rule.getRuleCode(), instanceId, true);
            validateDurationAgainstInterval(
                    cfg.getConditionConfig() != null ? cfg.getConditionConfig() : rule.getConditionConfig(),
                    cfg.getRecoveryConfig() != null ? cfg.getRecoveryConfig() : rule.getRecoveryConfig(),
                    scanIntervalMin);
            cfg.setRuleName(rule.getRuleName());
            cfg.setScanIntervalMin(scanIntervalMin);
            cfg.setUpdatedAt(OffsetDateTime.now());
            instanceConfigMapper.updateById(cfg);
            return;
        }
        AlertRuleInstanceConfig customCfg = loadCustomConfigByRuleCode(ruleCode);
        if (customCfg == null) {
            throw new IllegalArgumentException("规则不存在：" + ruleCode);
        }
        checkInstanceAccessible(customCfg.getInstanceId());
        List<String> metricCodes = StringUtils.hasText(customCfg.getMetricName())
                ? List.of(customCfg.getMetricName().trim()) : Collections.emptyList();
        IntervalCapability capability = computeIntervalCapability(metricCodes);
        validateScanInterval(scanIntervalMin, capability);
        validateDurationAgainstInterval(customCfg.getConditionConfig(), customCfg.getRecoveryConfig(), scanIntervalMin);
        customCfg.setScanIntervalMin(scanIntervalMin);
        customCfg.setUpdatedAt(OffsetDateTime.now());
        instanceConfigMapper.updateById(customCfg);
    }

    // ── 内置规则模板全局维护（系统设置 → 内置规则管理） ──────────────────────

    /** 目标库 SQL 规则默认扫描间隔（分钟）：巡检类检查不需要分钟级频率，降低目标库压力。 */
    private static final int DEFAULT_TARGET_SQL_INTERVAL_MIN = 5;

    private static final String DATA_SOURCE_METRIC = "metric";
    private static final String DATA_SOURCE_TARGET_SQL = "target_sql";

    @Override
    public PageResult<AlertRuleVo> pageBuiltinTemplates(BuiltinRulePageRequest req) {
        LambdaQueryWrapper<AlertRule> w = new LambdaQueryWrapper<AlertRule>()
                .eq(req.getDbTypeId() != null, AlertRule::getDbTypeId, req.getDbTypeId())
                .eq(StringUtils.hasText(req.getRuleLevel()), AlertRule::getRuleLevel, req.getRuleLevel())
                .orderByAsc(AlertRule::getRuleLevel)
                .orderByDesc(AlertRule::getCreatedAt);
        List<AlertRule> rules = alertRuleMapper.selectList(w);
        rules.forEach(r -> r.setRuleType("builtin"));

        String keyword = StringUtils.hasText(req.getKeyword()) ? req.getKeyword().trim() : null;
        String dataSource = StringUtils.hasText(req.getDataSource()) ? req.getDataSource().trim() : null;
        List<AlertRule> filtered = rules.stream().filter(r -> {
            if (keyword != null
                    && !(r.getRuleName() != null && r.getRuleName().contains(keyword))
                    && !(r.getRuleCode() != null && r.getRuleCode().contains(keyword))) {
                return false;
            }
            if (dataSource != null && !dataSource.equals(resolveDataSource(r.getConditionConfig()))) {
                return false;
            }
            return true;
        }).toList();

        long total = filtered.size();
        int from = (int) ((req.getPageNum() - 1) * req.getPageSize());
        int to = (int) Math.min(from + req.getPageSize(), total);
        List<AlertRule> paged = (from >= total) ? Collections.emptyList() : filtered.subList(from, to);

        Function<Long, String> typeNameFn = buildTypeNameFn();
        Function<Long, String> versionCodeFn = buildVersionCodeFn();
        List<Long> ruleIds = paged.stream().map(AlertRule::getId).toList();
        Map<Long, List<String>> metricCodesByRule = loadMetricCodesByRule(ruleIds);
        Map<String, Integer> enabledCountByCode = countEnabledInstances(
                paged.stream().map(AlertRule::getRuleCode).toList());

        List<AlertRuleVo> vos = paged.stream()
                .map(r -> {
                    AlertRuleVo vo = toVo(r, 0, null, null, null, typeNameFn, versionCodeFn,
                            metricCodesByRule.getOrDefault(r.getId(), Collections.emptyList()));
                    vo.setEnabledInstanceCount(enabledCountByCode.getOrDefault(r.getRuleCode(), 0));
                    return vo;
                })
                .toList();
        return PageResult.of(vos, total);
    }

    @Override
    @Transactional
    public AlertRuleVo saveBuiltinTemplate(BuiltinRuleSaveRequest req) {
        boolean isNew = req.getId() == null;
        AlertRule rule;
        if (isNew) {
            String ruleCode = StringUtils.hasText(req.getRuleCode()) ? req.getRuleCode().trim() : null;
            if (!StringUtils.hasText(ruleCode)) {
                throw new IllegalArgumentException("规则编码不能为空");
            }
            if (ruleCode.startsWith("custom.")) {
                throw new IllegalArgumentException("内置规则编码不允许以 custom. 开头");
            }
            boolean codeExists = alertRuleMapper.selectCount(new LambdaQueryWrapper<AlertRule>()
                    .eq(AlertRule::getRuleCode, ruleCode)) > 0
                    || instanceConfigMapper.selectCount(new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                    .eq(AlertRuleInstanceConfig::getRuleCode, ruleCode)) > 0;
            if (codeExists) {
                throw new IllegalArgumentException("规则编码已存在：" + ruleCode);
            }
            rule = new AlertRule();
            rule.setRuleCode(ruleCode);
            rule.setCreatedBy(resolveOperatorName(currentUserId()));
            rule.setCreatedAt(OffsetDateTime.now());
        } else {
            rule = alertRuleMapper.selectById(req.getId());
            if (rule == null) {
                throw new IllegalArgumentException("内置规则不存在：id=" + req.getId());
            }
            if (StringUtils.hasText(req.getRuleCode())
                    && !req.getRuleCode().trim().equals(rule.getRuleCode())) {
                throw new IllegalArgumentException("内置规则编码不允许修改");
            }
        }
        DbType ruleDbType = requireDbType(req.getDbTypeId());
        rule.setRuleType("builtin");
        rule.setRuleName(req.getRuleName().trim());
        rule.setRuleLevel(req.getRuleLevel().trim());
        rule.setDbTypeId(req.getDbTypeId());
        rule.setDbVersionIds(req.getDbVersionIds() == null || req.getDbVersionIds().isEmpty()
                ? null : req.getDbVersionIds());
        rule.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription().trim() : null);

        boolean targetSql = DATA_SOURCE_TARGET_SQL.equals(req.getDataSource())
                || (!StringUtils.hasText(req.getDataSource()) && StringUtils.hasText(req.getCustomSql()));

        // 模板保存为全量表单提交，conditionConfig 整体重建（不做旧值合并），避免残留已删除的配置项
        Map<String, Object> cond = new HashMap<>();
        if (!StringUtils.hasText(req.getOperator())) {
            throw new IllegalArgumentException("必须配置比较运算符");
        }
        if (req.getThreshold() == null) {
            throw new IllegalArgumentException("必须配置触发阈值");
        }
        cond.put("operator", req.getOperator().trim());
        cond.put("threshold", req.getThreshold());
        putIfNotNull(cond, "unit", StringUtils.hasText(req.getUnit()) ? req.getUnit().trim() : null);
        putIfNotNull(cond, "duration", req.getDuration());

        // 布尔型（状态类）规则标记与友好文案：超管在模板管理中可配置；
        // 请求未携带 booleanCondition 时沿用原模板标记（兼容不感知该字段的调用方）
        boolean booleanCondition = req.getBooleanCondition() != null
                ? req.getBooleanCondition()
                : (!isNew && isConditionLocked(rule.getConditionConfig()));
        if (booleanCondition) {
            cond.put("conditionType", "boolean");
            String condDisplay = StringUtils.hasText(req.getConditionDisplay())
                    ? req.getConditionDisplay().trim()
                    : getFrom(rule.getConditionConfig(), "displayText");
            putIfNotNull(cond, "displayText", condDisplay);
        }

        List<String> metricCodes;
        if (targetSql) {
            String customSql = StringUtils.hasText(req.getCustomSql()) ? req.getCustomSql().trim() : null;
            if (customSql == null) {
                throw new IllegalArgumentException("目标库 SQL 规则必须配置查询语句");
            }
            SqlSafetyValidator.validateQueryOnly(customSql, ruleDbType);
            cond.put("customSql", customSql);
            String resultMode = StringUtils.hasText(req.getResultMode())
                    ? req.getResultMode().trim().toLowerCase() : "single";
            cond.put("resultMode", resultMode);
            if ("multi".equals(resultMode)) {
                putIfNotNull(cond, "entityColumn",
                        StringUtils.hasText(req.getEntityColumn()) ? req.getEntityColumn().trim() : null);
                putIfNotNull(cond, "valueColumn",
                        StringUtils.hasText(req.getValueColumn()) ? req.getValueColumn().trim() : null);
                cond.put("displayTemplate", normalizeDisplayTemplate(req.getDisplayTemplate()));
            } else {
                putIfNotNull(cond, "sqlReturnField",
                        StringUtils.hasText(req.getSqlReturnField()) ? req.getSqlReturnField().trim() : null);
            }
            rule.setMetricName(null);
            metricCodes = Collections.emptyList();
        } else {
            if (!StringUtils.hasText(req.getMetricName())) {
                throw new IllegalArgumentException("产品库指标规则必须选择监控指标");
            }
            rule.setMetricName(req.getMetricName().trim());
            List<String> normalized = normalizeMetricCodes(req.getMetricCodes());
            metricCodes = normalized.isEmpty() ? List.of(rule.getMetricName()) : normalized;
        }
        rule.setConditionConfig(cond);

        Map<String, Object> recovery = new HashMap<>();
        putIfNotNull(recovery, "operator",
                StringUtils.hasText(req.getRecoveryOperator()) ? req.getRecoveryOperator().trim() : null);
        putIfNotNull(recovery, "threshold", req.getRecoveryThreshold());
        putIfNotNull(recovery, "duration", req.getRecoveryDuration());
        if (booleanCondition) {
            String recDisplay = StringUtils.hasText(req.getRecoveryDisplay())
                    ? req.getRecoveryDisplay().trim()
                    : getFrom(rule.getRecoveryConfig(), "displayText");
            putIfNotNull(recovery, "displayText", recDisplay);
        }
        rule.setRecoveryConfig(recovery.isEmpty() ? null : recovery);

        applyNotificationConfig(rule, req);

        IntervalCapability capability = computeIntervalCapability(metricCodes);
        int interval = req.getScanIntervalMin() != null
                ? req.getScanIntervalMin()
                : (targetSql ? DEFAULT_TARGET_SQL_INTERVAL_MIN : capability.minAllowedIntervalMin);
        validateScanInterval(interval, capability);
        validateDurationAgainstInterval(cond, rule.getRecoveryConfig(), interval);
        rule.setScanIntervalMin(interval);
        rule.setScanIntervalSource(req.getScanIntervalMin() != null ? "USER_OVERRIDE" : "SYSTEM_DEFAULT");
        rule.setUpdatedAt(OffsetDateTime.now());

        if (isNew) {
            alertRuleMapper.insert(rule);
        } else {
            alertRuleMapper.updateById(rule);
        }
        syncMetricRefs(rule.getId(), metricCodes);
        return toVo(rule, 0, null, null, null, buildTypeNameFn(), buildVersionCodeFn(), metricCodes);
    }

    @Override
    @Transactional
    public void deleteBuiltinTemplate(String ruleCode) {
        AlertRule rule = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getRuleCode, ruleCode)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new IllegalArgumentException("内置规则不存在：" + ruleCode);
        }
        // 级联清理：先关闭各实例活跃事件（含持续窗口），再删除实例配置与指标关联
        List<AlertRuleInstanceConfig> cfgs = instanceConfigMapper.selectList(
                new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                        .eq(AlertRuleInstanceConfig::getRuleCode, rule.getRuleCode())
                        .eq(AlertRuleInstanceConfig::getRuleType, "builtin"));
        for (AlertRuleInstanceConfig cfg : cfgs) {
            closeActiveEventsByRule(rule.getRuleCode(), cfg.getInstanceId());
        }
        if (!cfgs.isEmpty()) {
            instanceConfigMapper.deleteBatchIds(cfgs.stream().map(AlertRuleInstanceConfig::getId).toList());
        }
        metricRefMapper.delete(new LambdaQueryWrapper<AlertRuleMetricRef>()
                .eq(AlertRuleMetricRef::getRuleId, rule.getId()));
        alertRuleMapper.deleteById(rule.getId());
    }

    /**
     * 条件是否被系统锁定（布尔型规则，conditionConfig.conditionType = boolean）。
     * <p>复制线程停止、MGR 成员异常等规则的指标是 0/1 布尔值，阈值调整无意义；
     * 锁定后用户仅可修改告警级别、扫描间隔、通知设置与启停，条件类字段一律以模板为准。
     */
    private boolean isConditionLocked(Map<String, Object> conditionConfig) {
        return "boolean".equals(getFrom(conditionConfig, "conditionType"));
    }

    /** 模板通知默认值整体重建（saveBuiltinTemplate 普通/锁定两条路径共用）。 */
    private void applyNotificationConfig(AlertRule rule, BuiltinRuleSaveRequest req) {
        Map<String, Object> notify = new HashMap<>();
        putIfNotNull(notify, "notifyOnTrigger", req.getNotifyOnTrigger());
        putIfNotNull(notify, "notifyOnRecovery", req.getNotifyOnRecovery());
        putIfNotNull(notify, "channelEmail", req.getChannelEmail());
        putIfNotNull(notify, "channelSms", req.getChannelSms());
        putIfNotNull(notify, "channelWebhook", req.getChannelWebhook());
        putIfNotNull(notify, "channelDingtalk", req.getChannelDingtalk());
        putIfNotNull(notify, "channelWecom", req.getChannelWecom());
        putIfNotNull(notify, "channelFeishu", req.getChannelFeishu());
        putIfNotNull(notify, "silencePeriod", req.getSilencePeriod());
        rule.setNotificationConfig(notify.isEmpty() ? null : notify);
    }

    /** 锁定条件规则的实例级保存：剥离请求中的条件类字段，仅保留级别/调频/通知/启停覆盖。 */
    private void stripConditionFields(AlertRuleSaveRequest req) {
        req.setMetricName(null);
        req.setOperator(null);
        req.setThreshold(null);
        req.setUnit(null);
        req.setDuration(null);
        req.setRecoveryOperator(null);
        req.setRecoveryThreshold(null);
        req.setRecoveryDuration(null);
        req.setDescription(null);
    }

    /** 由生效 conditionConfig 推导数据来源字典值：携带 customSql 即为目标库 SQL 评估。 */
    private String resolveDataSource(Map<String, Object> conditionConfig) {
        String customSql = getFrom(conditionConfig, "customSql");
        return StringUtils.hasText(customSql) ? DATA_SOURCE_TARGET_SQL : DATA_SOURCE_METRIC;
    }

    /** 统计各规则已启用的实例数（内置规则管理列表展示用）。 */
    private Map<String, Integer> countEnabledInstances(List<String> ruleCodes) {
        if (ruleCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> counts = new HashMap<>();
        instanceConfigMapper.selectList(new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                        .select(AlertRuleInstanceConfig::getRuleCode)
                        .in(AlertRuleInstanceConfig::getRuleCode, ruleCodes)
                        .eq(AlertRuleInstanceConfig::getRuleType, "builtin")
                        .eq(AlertRuleInstanceConfig::getEnabled, true))
                .forEach(c -> counts.merge(c.getRuleCode(), 1, Integer::sum));
        return counts;
    }

    /** 同步模板依赖指标关联表：整体重建（删除旧关联后插入新列表）。 */
    private void syncMetricRefs(Long ruleId, List<String> metricCodes) {
        metricRefMapper.delete(new LambdaQueryWrapper<AlertRuleMetricRef>()
                .eq(AlertRuleMetricRef::getRuleId, ruleId));
        OffsetDateTime now = OffsetDateTime.now();
        for (String code : metricCodes) {
            AlertRuleMetricRef ref = new AlertRuleMetricRef();
            ref.setRuleId(ruleId);
            ref.setMetricCode(code);
            ref.setCreatedAt(now);
            ref.setUpdatedAt(now);
            metricRefMapper.insert(ref);
        }
    }

    // ── 私有辅助 ─────────────────────────────────────────────────────────────

    /**
     * 查询适用于指定实例类型 + 版本的内置规则。
     * <p>过滤逻辑：
     * <ul>
     *   <li>dbTypeId 必填并严格匹配；缺失类型时不返回任何内置规则</li>
     *   <li>dbVersionIds 为 null/空 → 适用所有版本；否则包含实例的 dbVersionId</li>
     * </ul>
     */
    private List<AlertRule> queryBuiltins(Long instDbTypeId, Long instDbVersionId) {
        if (instDbTypeId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AlertRule> w = new LambdaQueryWrapper<>();
        w.eq(AlertRule::getDbTypeId, instDbTypeId);
        w.orderByAsc(AlertRule::getRuleLevel);
        List<AlertRule> rules = alertRuleMapper.selectList(w);
        rules.forEach(r -> r.setRuleType("builtin"));

        // 内存过滤版本：规则 dbVersionIds 为空表示通配，否则须包含实例版本 ID
        if (instDbVersionId != null) {
            final Long vid = instDbVersionId;
            rules = rules.stream()
                    .filter(r -> r.getDbVersionIds() == null
                            || r.getDbVersionIds().isEmpty()
                            || r.getDbVersionIds().contains(vid))
                    .toList();
        }
        return rules;
    }

    /**
     * 追加 HOST 主机内置规则（host.* 指标，db_type_id 指向 database_type code=HOST）。
     * 仅对关联了主机的实例调用：主机指标经扇出写入该实例的 metric_data_1m，规则可正常评估。
     */
    private List<AlertRule> appendHostBuiltins(List<AlertRule> builtins) {
        DatabaseType hostType = databaseTypeMapper.selectOne(
                new LambdaQueryWrapper<DatabaseType>()
                        .eq(DatabaseType::getCode, "HOST")
                        .last("LIMIT 1"));
        if (hostType == null || hostType.getId() == null) {
            return builtins;
        }
        List<AlertRule> hostRules = alertRuleMapper.selectList(
                new LambdaQueryWrapper<AlertRule>()
                        .eq(AlertRule::getDbTypeId, hostType.getId())
                        .orderByAsc(AlertRule::getRuleLevel));
        if (hostRules.isEmpty()) {
            return builtins;
        }
        hostRules.forEach(r -> r.setRuleType("builtin"));
        List<AlertRule> merged = new ArrayList<>(builtins);
        merged.addAll(hostRules);
        return merged;
    }

    /** 构建 dbTypeId → 类型名称（label）的查找函数，懒加载全量缓存。 */
    private Function<Long, String> buildTypeNameFn() {
        Map<Long, String> cache = databaseTypeMapper.selectList(null)
                .stream()
                .filter(dt -> dt.getId() != null)
                .collect(Collectors.toMap(DatabaseType::getId, DatabaseType::getLabel,
                        (a, b) -> a));
        return id -> id == null ? null : cache.get(id);
    }

    /** 构建 dbVersionId → versionCode 的查找函数，懒加载全量缓存。 */
    private Function<Long, String> buildVersionCodeFn() {
        Map<Long, String> cache = databaseVersionMapper.selectList(null)
                .stream()
                .filter(dv -> dv.getId() != null)
                .collect(Collectors.toMap(DatabaseVersion::getId, DatabaseVersion::getVersionCode,
                        (a, b) -> a));
        return id -> id == null ? null : cache.get(id);
    }

    private List<AlertRuleInstanceConfig> loadCustomConfigsByInstance(Long instanceId) {
        return instanceConfigMapper.selectList(
                new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                        .eq(AlertRuleInstanceConfig::getInstanceId, instanceId)
                        .eq(AlertRuleInstanceConfig::getRuleType, "custom")
                        .orderByDesc(AlertRuleInstanceConfig::getCreatedAt));
    }

    private AlertRuleInstanceConfig loadCustomConfigById(Long id) {
        AlertRuleInstanceConfig cfg = instanceConfigMapper.selectById(Objects.requireNonNull(id, "id"));
        if (cfg == null || !"custom".equals(cfg.getRuleType())) {
            return null;
        }
        return cfg;
    }

    private AlertRuleInstanceConfig loadCustomConfigByRuleCode(String ruleCode) {
        if (!StringUtils.hasText(ruleCode)) {
            return null;
        }
        AlertRuleInstanceConfig cfg = instanceConfigMapper.selectOne(
                new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                        .eq(AlertRuleInstanceConfig::getRuleCode, ruleCode.trim())
                        .eq(AlertRuleInstanceConfig::getRuleType, "custom")
                        .last("LIMIT 1"));
        if (cfg == null) {
            return null;
        }
        return cfg;
    }

    private AlertRule materializeCustomRule(AlertRuleInstanceConfig cfg) {
        AlertRule rule = new AlertRule();
        rule.setId(stableIdFromRuleCode(cfg.getRuleCode()));
        rule.setRuleName(cfg.getRuleName());
        rule.setRuleCode(cfg.getRuleCode());
        rule.setRuleType("custom");
        rule.setRuleLevel(cfg.getRuleLevel());
        rule.setDescription(cfg.getDescription());
        rule.setMetricName(cfg.getMetricName());
        rule.setConditionConfig(cfg.getConditionConfig());
        rule.setRecoveryConfig(cfg.getRecoveryConfig());
        rule.setNotificationConfig(cfg.getNotificationConfig());
        rule.setScanIntervalMin(cfg.getScanIntervalMin());
        rule.setScanIntervalSource("USER_OVERRIDE");
        rule.setCreatedAt(cfg.getCreatedAt());
        rule.setUpdatedAt(cfg.getUpdatedAt());
        return rule;
    }

    private AlertRuleInstanceConfig saveCustomInstanceConfig(AlertRuleInstanceConfig cfg, AlertRuleSaveRequest req, DbType dbType) {
        boolean isNew = cfg == null;
        String incomingRuleCode = StringUtils.hasText(req.getRuleCode()) ? req.getRuleCode().trim() : null;
        if (StringUtils.hasText(incomingRuleCode) && !incomingRuleCode.startsWith("custom.")) {
            throw new IllegalArgumentException("自定义规则 ruleCode 必须以 custom. 开头");
        }
        if (cfg == null) {
            cfg = new AlertRuleInstanceConfig();
            cfg.setRuleType("custom");
            cfg.setInstanceId(req.getInstanceId());
            cfg.setRuleCode(StringUtils.hasText(incomingRuleCode) ? incomingRuleCode : generateCustomRuleCode());
            cfg.setCreatedAt(OffsetDateTime.now());
        } else if (!Objects.equals(cfg.getInstanceId(), req.getInstanceId())) {
            throw new IllegalArgumentException("不允许跨实例修改自定义规则");
        } else if (StringUtils.hasText(incomingRuleCode) && !incomingRuleCode.equals(cfg.getRuleCode())) {
            throw new IllegalArgumentException("不允许修改自定义规则 ruleCode");
        }
        if (!StringUtils.hasText(cfg.getRuleCode())) {
            cfg.setRuleCode(StringUtils.hasText(incomingRuleCode) ? incomingRuleCode : generateCustomRuleCode());
        }
        cfg.setEnabled(req.getEnabled() != null ? req.getEnabled() : Boolean.TRUE);
        cfg.setRuleName(req.getRuleName());
        cfg.setRuleLevel(req.getRuleLevel());
        cfg.setDescription(req.getDescription());
        cfg.setMetricName(req.getMetricName());
        Map<String, Object> cond = cfg.getConditionConfig() != null
                ? new HashMap<>(cfg.getConditionConfig()) : new HashMap<>();
        putIfNotNull(cond, "operator", req.getOperator());
        putIfNotNull(cond, "threshold", req.getThreshold());
        putIfNotNull(cond, "unit", req.getUnit());
        putIfNotNull(cond, "duration", req.getDuration());
        putIfNotNull(cond, "customSql", req.getCustomSql());
        putIfNotNull(cond, "resultMode", req.getResultMode());
        putIfNotNull(cond, "sqlReturnField", req.getSqlReturnField());
        putIfNotNull(cond, "entityColumn", req.getEntityColumn());
        putIfNotNull(cond, "valueColumn", req.getValueColumn());
        String resultMode = req.getResultMode();
        if (!StringUtils.hasText(resultMode)) {
            resultMode = getFrom(cond, "resultMode");
        }
        if ("multi".equalsIgnoreCase(resultMode)) {
            cond.put("displayTemplate", normalizeDisplayTemplate(req.getDisplayTemplate()));
        } else {
            cond.remove("displayTemplate");
        }
        String customSql = getFrom(cond, "customSql");
        if (StringUtils.hasText(customSql)) {
            SqlSafetyValidator.validateQueryOnly(customSql, dbType);
        }
        // 依赖指标编码列表落库（自定义规则整体存于 instance_config，故随 conditionConfig 一并持久化，
        // 避免请求携带 metricCodes 却被静默丢弃）。
        List<String> normalizedMetricCodes = normalizeMetricCodes(req.getMetricCodes());
        if (!normalizedMetricCodes.isEmpty()) {
            cond.put("metricCodes", normalizedMetricCodes);
        } else {
            cond.remove("metricCodes");
        }
        // 必填校验：自定义规则必须"指标阈值"或"自定义 SQL"二选一有效，否则评估器永远不会触发，
        // 属于无效配置，保存阶段直接拒绝而非静默入库成"僵尸规则"。
        validateCustomRuleEvaluable(cfg.getMetricName(), cond);
        cfg.setConditionConfig(cond);

        Map<String, Object> recovery = cfg.getRecoveryConfig() != null
                ? new HashMap<>(cfg.getRecoveryConfig()) : new HashMap<>();
        putIfNotNull(recovery, "operator", req.getRecoveryOperator());
        putIfNotNull(recovery, "threshold", req.getRecoveryThreshold());
        putIfNotNull(recovery, "duration", req.getRecoveryDuration());
        cfg.setRecoveryConfig(recovery);

        Map<String, Object> notify = cfg.getNotificationConfig() != null
                ? new HashMap<>(cfg.getNotificationConfig()) : new HashMap<>();
        putIfNotNull(notify, "notifyOnTrigger", req.getNotifyOnTrigger());
        putIfNotNull(notify, "notifyOnRecovery", req.getNotifyOnRecovery());
        putIfNotNull(notify, "channelEmail", req.getChannelEmail());
        putIfNotNull(notify, "channelSms", req.getChannelSms());
        putIfNotNull(notify, "channelWebhook", req.getChannelWebhook());
        putIfNotNull(notify, "channelDingtalk", req.getChannelDingtalk());
        putIfNotNull(notify, "channelWecom", req.getChannelWecom());
        putIfNotNull(notify, "channelFeishu", req.getChannelFeishu());
        putIfNotNull(notify, "silencePeriod", req.getSilencePeriod());
        cfg.setNotificationConfig(notify);

        List<String> metricCodes = StringUtils.hasText(cfg.getMetricName())
                ? List.of(cfg.getMetricName().trim()) : Collections.emptyList();
        IntervalCapability capability = computeIntervalCapability(metricCodes);
        int interval = req.getScanIntervalMin() != null
                ? req.getScanIntervalMin()
                : Objects.requireNonNullElse(cfg.getScanIntervalMin(), capability.minAllowedIntervalMin);
        validateScanInterval(interval, capability);
        validateDurationAgainstInterval(cfg.getConditionConfig(), cfg.getRecoveryConfig(), interval);
        cfg.setScanIntervalMin(interval);
        cfg.setUpdatedAt(OffsetDateTime.now());
        if (isNew) {
            instanceConfigMapper.insert(cfg);
        } else {
            instanceConfigMapper.updateById(cfg);
        }
        return cfg;
    }

    /** 归一化依赖指标编码列表：去空白、去重、保序。 */
    private List<String> normalizeMetricCodes(List<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return metricCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** 读取自定义规则的依赖指标编码：优先取 conditionConfig.metricCodes，回退到单指标 metricName。 */
    @SuppressWarnings("unchecked")
    private List<String> loadCustomMetricCodes(AlertRuleInstanceConfig cfg) {
        Map<String, Object> cond = cfg.getConditionConfig();
        if (cond != null && cond.get("metricCodes") instanceof List<?> list && !list.isEmpty()) {
            return list.stream()
                    .filter(o -> o instanceof String s && StringUtils.hasText(s))
                    .map(o -> ((String) o).trim())
                    .distinct()
                    .toList();
        }
        return StringUtils.hasText(cfg.getMetricName())
                ? List.of(cfg.getMetricName().trim()) : Collections.emptyList();
    }

    /**
     * 校验自定义规则至少存在一条可被评估的路径：
     * <ul>
     *   <li>自定义 SQL 模式：{@code customSql} 非空即可（阈值判定在 SQL 结果之上）；</li>
     *   <li>指标阈值模式：必须同时具备 {@code metricName}、{@code operator}、可解析的 {@code threshold}。</li>
     * </ul>
     */
    private void validateCustomRuleEvaluable(String metricName, Map<String, Object> cond) {
        String customSql = getFrom(cond, "customSql");
        if (StringUtils.hasText(customSql)) {
            return;
        }
        if (!StringUtils.hasText(metricName)) {
            throw new IllegalArgumentException("自定义规则必须配置监控指标或自定义 SQL");
        }
        String operator = getFrom(cond, "operator");
        if (!StringUtils.hasText(operator)) {
            throw new IllegalArgumentException("指标阈值规则必须配置比较运算符");
        }
        if (parseThreshold(cond.get("threshold")) == null) {
            throw new IllegalArgumentException("指标阈值规则必须配置有效的触发阈值");
        }
    }

    /** 宽松解析阈值（兼容数值 / 字符串），无法解析返回 null。 */
    private Double parseThreshold(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s && StringUtils.hasText(s)) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, AlertRuleInstanceConfig> loadOverrideConfigs(Long instanceId, List<String> ruleCodes) {
        if (instanceId == null || ruleCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return instanceConfigMapper.selectList(
                new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                        .in(AlertRuleInstanceConfig::getRuleCode, ruleCodes)
                        .eq(AlertRuleInstanceConfig::getInstanceId, instanceId)
                        .eq(AlertRuleInstanceConfig::getRuleType, "builtin"))
                .stream()
                .collect(Collectors.toMap(
                        AlertRuleInstanceConfig::getRuleCode,
                        Function.identity(),
                        (a, b) -> a));
    }

    /** 启停状态统一由 alert_rule_instance_config.enabled 承载：内置规则查覆盖配置，自定义规则查自身配置行。 */
    private boolean resolveInstanceEnabled(AlertRule rule, Map<String, AlertRuleInstanceConfig> overrideMap,
                                           Map<String, Boolean> customEnabledMap) {
        if ("builtin".equals(rule.getRuleType())) {
            AlertRuleInstanceConfig cfg = overrideMap.get(rule.getRuleCode());
            return cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
        }
        return Boolean.TRUE.equals(customEnabledMap.get(rule.getRuleCode()));
    }

    private Map<Long, List<String>> loadMetricCodesByRule(List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return metricRefMapper.selectList(new LambdaQueryWrapper<AlertRuleMetricRef>()
                        .in(AlertRuleMetricRef::getRuleId, ruleIds)
                        .orderByAsc(AlertRuleMetricRef::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        AlertRuleMetricRef::getRuleId,
                        Collectors.mapping(AlertRuleMetricRef::getMetricCode, Collectors.toList())));
    }

    private void closeActiveEventsByRule(String ruleCode, Long instanceId) {
        if (!StringUtils.hasText(ruleCode) || instanceId == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String exactDedupKey = ruleCode.trim() + ":" + instanceId;
        String dimensionPrefix = exactDedupKey + ":";
        // 无论是否存在活跃事件，都先清理该规则+实例的持续窗口（含尚未生成事件的 pending 窗口），
        // 否则重新启用规则后残留窗口会使新触发绕过持续时长判定。
        evaluateWindowMapper.deleteByRuleInstance(exactDedupKey, dimensionPrefix);
        // 先查出将被关闭的活跃事件（含 fromStatus/eventCode 快照），用于补齐操作审计流水，
        // 与人工关闭保持一致：不写审计会造成"事件被关闭却无操作记录"的审计断链。
        List<AlertEvent> affected = alertEventMapper.selectList(
                new LambdaQueryWrapper<AlertEvent>()
                        .select(AlertEvent::getId, AlertEvent::getEventCode, AlertEvent::getStatus)
                        .in(AlertEvent::getStatus, ACTIVE_EVENT_STATUSES)
                        .and(w -> w.eq(AlertEvent::getDedupKey, exactDedupKey)
                                .or()
                                .likeRight(AlertEvent::getDedupKey, dimensionPrefix)));
        if (affected.isEmpty()) {
            return;
        }
        Long operatorId = currentUserId();
        String operatorName = resolveOperatorName(operatorId);
        String remark = "规则停用/删除，系统联动关闭关联告警事件";
        for (AlertEvent e : affected) {
            // 逐事件带 status 守卫：查询到更新之间事件可能已被评估线程置为 recovered，
            // 不守卫会把 recovered 改写回 closed，违反"recovered 仅由评估/自愈写入"的状态机约定；
            // 审计流水仅对实际更新成功的事件写入，避免"流水声称已关闭但 DB 未变更"的断链
            int updated = alertEventMapper.update(
                    null,
                    new LambdaUpdateWrapper<AlertEvent>()
                            .eq(AlertEvent::getId, e.getId())
                            .in(AlertEvent::getStatus, ACTIVE_EVENT_STATUSES)
                            .set(AlertEvent::getStatus, "closed")
                            .set(AlertEvent::getCloseUserId, operatorId)
                            // 与人工关闭保持一致的短期抑制窗口：规则重新启用后若指标仍异常，
                            // 不会在窗口内立即重复建单。
                            // 不写 recovery_time：指标可能仍处于异常态，恢复时间只由评估器/自愈路径写入。
                            .set(AlertEvent::getSilenceUntilTime, now.plusHours(RULE_DISABLE_SUPPRESS_HOURS))
                            // 同步写 last_remark，列表页"最近处置说明"与操作流水保持一致
                            .set(AlertEvent::getLastRemark, remark)
                            .set(AlertEvent::getUpdatedAt, now)
            );
            if (updated == 0) {
                continue;
            }
            AlertEventOperateLog log = new AlertEventOperateLog();
            log.setEventId(e.getId());
            log.setEventCode(e.getEventCode());
            log.setOperateType("close");
            log.setFromStatus(e.getStatus());
            log.setToStatus("closed");
            log.setOperatorId(operatorId);
            log.setOperatorName(operatorName);
            log.setRemark(remark);
            log.setCreatedAt(now);
            operateLogMapper.insert(log);
        }
    }

    /** 当前登录用户 ID；后台/系统调用（无线程上下文）返回 null。 */
    private Long currentUserId() {
        CurrentUserHolder.Current current = CurrentUserHolder.get();
        return current == null ? null : current.userId();
    }

    /** 解析操作人姓名快照：有用户取昵称/用户名，无用户（系统联动）标注为"系统"。 */
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

    private IntervalCapability computeIntervalCapability(List<String> metricCodes) {
        int metricSamplingMax = SYSTEM_MIN_INTERVAL_MINUTES;
        List<String> codes = metricCodes == null ? Collections.emptyList() : metricCodes;
        if (!codes.isEmpty()) {
            List<MetricDefinition> defs = metricDefinitionMapper.selectList(
                    new LambdaQueryWrapper<MetricDefinition>()
                            .in(MetricDefinition::getMetricCode, codes)
                            .select(MetricDefinition::getMetricCode, MetricDefinition::getFrequency));
            metricSamplingMax = defs.stream()
                    .map(MetricDefinition::getFrequency)
                    .filter(StringUtils::hasText)
                    .map(this::parseFrequencyToMinutes)
                    .max(Comparator.naturalOrder())
                    .orElse(SYSTEM_MIN_INTERVAL_MINUTES);
        }
        int minAllowed = Math.max(SYSTEM_MIN_INTERVAL_MINUTES, metricSamplingMax);
        return new IntervalCapability(SYSTEM_MIN_INTERVAL_MINUTES, metricSamplingMax, minAllowed);
    }

    private int parseFrequencyToMinutes(String frequency) {
        String normalized = frequency.trim().toLowerCase();
        if (normalized.endsWith("m")) {
            return parsePositiveInt(normalized.substring(0, normalized.length() - 1));
        }
        if (normalized.endsWith("h")) {
            return parsePositiveInt(normalized.substring(0, normalized.length() - 1)) * 60;
        }
        if (normalized.endsWith("d")) {
            return parsePositiveInt(normalized.substring(0, normalized.length() - 1)) * 1440;
        }
        return SYSTEM_MIN_INTERVAL_MINUTES;
    }

    private int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(parsed, SYSTEM_MIN_INTERVAL_MINUTES);
        } catch (NumberFormatException e) {
            return SYSTEM_MIN_INTERVAL_MINUTES;
        }
    }

    private void validateScanInterval(int scanIntervalMin, IntervalCapability capability) {
        if (scanIntervalMin < capability.minAllowedIntervalMin) {
            throw new IllegalArgumentException("扫描间隔不能小于最小允许值：" + capability.minAllowedIntervalMin + " 分钟");
        }
        if (ENFORCE_INTERVAL_MULTIPLE
                && scanIntervalMin % capability.minAllowedIntervalMin != 0) {
            throw new IllegalArgumentException("扫描间隔必须是最小允许值的整数倍：" + capability.minAllowedIntervalMin + " 分钟");
        }
    }

    /**
     * 校验持续时间与扫描间隔的关系：评估按扫描间隔离散执行，
     * duration 小于一个扫描间隔时会退化为"扫描间隔"，属无效配置，直接拒绝。
     */
    private void validateDurationAgainstInterval(Map<String, Object> conditionConfig,
                                                 Map<String, Object> recoveryConfig,
                                                 int scanIntervalMin) {
        int intervalSec = scanIntervalMin * 60;
        int duration = readDurationSeconds(conditionConfig);
        if (duration > 0 && duration < intervalSec) {
            throw new IllegalArgumentException(
                    "触发持续时间（" + duration + " 秒）不能小于扫描间隔（" + intervalSec + " 秒），请设为 0 或不小于扫描间隔");
        }
        int recoveryDuration = readDurationSeconds(recoveryConfig);
        if (recoveryDuration > 0 && recoveryDuration < intervalSec) {
            throw new IllegalArgumentException(
                    "恢复持续时间（" + recoveryDuration + " 秒）不能小于扫描间隔（" + intervalSec + " 秒），请设为 0 或不小于扫描间隔");
        }
    }

    private int readDurationSeconds(Map<String, Object> cfg) {
        if (cfg == null) {
            return 0;
        }
        Object raw = cfg.get("duration");
        if (raw instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        if (raw instanceof String s) {
            try {
                return Math.max(0, Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private AlertRuleInstanceConfig saveBuiltinInstanceConfig(AlertRule rule, AlertRuleSaveRequest req) {
        Long instanceId = Objects.requireNonNull(req.getInstanceId(), "instanceId");
        boolean enabled = req.getEnabled() == null || Boolean.TRUE.equals(req.getEnabled());
        AlertRuleInstanceConfig cfg = ensureBuiltinInstanceConfig(rule.getRuleCode(), instanceId, enabled);
        cfg.setRuleName(rule.getRuleName());
        cfg.setMetricName(StringUtils.hasText(req.getMetricName()) ? req.getMetricName().trim() : null);
        cfg.setRuleLevel(StringUtils.hasText(req.getRuleLevel()) ? req.getRuleLevel().trim() : null);
        cfg.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription().trim() : null);
        cfg.setConditionConfig(mergeJsonConfig(cfg.getConditionConfig(), req.getOperator(), req.getThreshold(),
                req.getUnit(), req.getDuration()));
        cfg.setRecoveryConfig(mergeJsonConfig(cfg.getRecoveryConfig(), req.getRecoveryOperator(),
                req.getRecoveryThreshold(), null, req.getRecoveryDuration()));
        Map<String, Object> notify = cfg.getNotificationConfig() != null
                ? new HashMap<>(cfg.getNotificationConfig()) : new HashMap<>();
        putIfNotNull(notify, "notifyOnTrigger", req.getNotifyOnTrigger());
        putIfNotNull(notify, "notifyOnRecovery", req.getNotifyOnRecovery());
        putIfNotNull(notify, "channelEmail", req.getChannelEmail());
        putIfNotNull(notify, "channelSms", req.getChannelSms());
        putIfNotNull(notify, "channelWebhook", req.getChannelWebhook());
        putIfNotNull(notify, "channelDingtalk", req.getChannelDingtalk());
        putIfNotNull(notify, "channelWecom", req.getChannelWecom());
        putIfNotNull(notify, "channelFeishu", req.getChannelFeishu());
        putIfNotNull(notify, "silencePeriod", req.getSilencePeriod());
        cfg.setNotificationConfig(notify);

        List<String> metricCodes = loadMetricCodesByRule(List.of(rule.getId()))
                .getOrDefault(rule.getId(), Collections.emptyList());
        if (StringUtils.hasText(cfg.getMetricName())) {
            metricCodes = List.of(cfg.getMetricName().trim());
        }
        IntervalCapability capability = computeIntervalCapability(metricCodes);
        Integer target = req.getScanIntervalMin();
        int interval = target != null
                ? target
                : Objects.requireNonNullElse(cfg.getScanIntervalMin(), rule.getScanIntervalMin());
        if (interval <= 0) {
            interval = capability.minAllowedIntervalMin;
        }
        validateScanInterval(interval, capability);
        validateDurationAgainstInterval(
                cfg.getConditionConfig() != null ? cfg.getConditionConfig() : rule.getConditionConfig(),
                cfg.getRecoveryConfig() != null ? cfg.getRecoveryConfig() : rule.getRecoveryConfig(),
                interval);
        cfg.setScanIntervalMin(interval);
        cfg.setUpdatedAt(OffsetDateTime.now());
        instanceConfigMapper.updateById(cfg);
        return cfg;
    }

    private Map<String, Object> mergeJsonConfig(Map<String, Object> base, String operator, Double threshold,
                                                String unit, Integer duration) {
        Map<String, Object> cfg = base != null ? new HashMap<>(base) : new HashMap<>();
        putIfNotNull(cfg, "operator", operator);
        putIfNotNull(cfg, "threshold", threshold);
        if (unit != null) {
            putIfNotNull(cfg, "unit", unit);
        }
        putIfNotNull(cfg, "duration", duration);
        return cfg;
    }

    private AlertRuleInstanceConfig ensureBuiltinInstanceConfig(String ruleCode, Long instanceId, boolean enabled) {
        AlertRuleInstanceConfig cfg = instanceConfigMapper.selectOne(
                new LambdaQueryWrapper<AlertRuleInstanceConfig>()
                        .eq(AlertRuleInstanceConfig::getRuleCode, ruleCode)
                        .eq(AlertRuleInstanceConfig::getInstanceId, instanceId)
                        .eq(AlertRuleInstanceConfig::getRuleType, "builtin")
                        .last("LIMIT 1"));
        if (cfg == null) {
            cfg = new AlertRuleInstanceConfig();
            cfg.setRuleCode(ruleCode);
            cfg.setInstanceId(instanceId);
            cfg.setRuleType("builtin");
            cfg.setEnabled(enabled);
            cfg.setCreatedAt(OffsetDateTime.now());
            cfg.setUpdatedAt(OffsetDateTime.now());
            instanceConfigMapper.insert(cfg);
            return cfg;
        }
        if (!StringUtils.hasText(cfg.getRuleType())) {
            cfg.setRuleType("builtin");
        }
        cfg.setEnabled(enabled);
        return cfg;
    }

    private String generateCustomRuleCode() {
        return "custom." + UUID.randomUUID().toString().replace("-", "");
    }

    private long stableIdFromRuleCode(String ruleCode) {
        if (!StringUtils.hasText(ruleCode)) {
            return 0L;
        }
        long hash = 1125899906842597L;
        String normalized = ruleCode.trim();
        for (int i = 0; i < normalized.length(); i++) {
            hash = 31 * hash + normalized.charAt(i);
        }
        return hash & ((1L << 53) - 1);
    }

    private void validateBuiltinRuleNameImmutable(AlertRule template, AlertRuleSaveRequest req) {
        if (!StringUtils.hasText(req.getRuleName())) {
            return;
        }
        String incoming = req.getRuleName().trim();
        String expected = template.getRuleName();
        if (!incoming.equals(expected)) {
            throw new IllegalArgumentException("内置规则名称不允许修改");
        }
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getFrom(Map<String, Object> map, String key) {
        return map == null ? null : (T) map.get(key);
    }

    /**
     * 配置叠加合并：以模板配置打底，实例覆盖配置逐字段叠加。
     * <p>内置规则的实例覆盖只写入用户可改的字段（operator/threshold/unit/duration），
     * 叠加合并可保留模板独有字段（如 customSql/resultMode），避免整包替换造成字段丢失。
     */
    private Map<String, Object> overlayConfig(Map<String, Object> base, Map<String, Object> override) {
        if (override == null || override.isEmpty()) {
            return base;
        }
        if (base == null || base.isEmpty()) {
            return override;
        }
        Map<String, Object> merged = new HashMap<>(base);
        merged.putAll(override);
        return merged;
    }

    private AlertRuleVo toVo(AlertRule r, int triggerCount,
                              OffsetDateTime lastTriggerAt, Boolean instanceEnabled,
                              AlertRuleInstanceConfig instanceCfg,
                              Function<Long, String> typeNameFn,
                              Function<Long, String> versionCodeFn,
                              List<String> metricCodes) {
        AlertRuleVo vo = new AlertRuleVo();
        vo.setId(r.getId());
        vo.setRuleName(instanceCfg != null && StringUtils.hasText(instanceCfg.getRuleName())
                ? instanceCfg.getRuleName() : r.getRuleName());
        vo.setRuleCode(r.getRuleCode());
        vo.setRuleType(r.getRuleType());
        // 解析类型 FK
        vo.setDbTypeId(r.getDbTypeId());
        vo.setDbType(typeNameFn.apply(r.getDbTypeId()));
        // 解析版本 FK：ID 列表 + 可读字符串（如 "5.7,8.0"）
        vo.setDbVersionIds(r.getDbVersionIds());
        if (r.getDbVersionIds() != null && !r.getDbVersionIds().isEmpty()) {
            String versionStr = r.getDbVersionIds().stream()
                    .map(versionCodeFn)
                    .filter(s -> s != null)
                    .collect(Collectors.joining(","));
            vo.setDbVersion(versionStr.isBlank() ? null : versionStr);
        }
        vo.setRuleLevel(r.getRuleLevel());
        vo.setRecommended(Boolean.TRUE.equals(r.getRecommended()));
        String overrideMetricName = instanceCfg != null ? instanceCfg.getMetricName() : null;
        vo.setMetricName(StringUtils.hasText(overrideMetricName) ? overrideMetricName : r.getMetricName());
        vo.setMetricCodes(metricCodes);
        if (instanceCfg != null && StringUtils.hasText(instanceCfg.getRuleLevel())) {
            vo.setRuleLevel(instanceCfg.getRuleLevel());
        }
        vo.setDescription(instanceCfg != null && StringUtils.hasText(instanceCfg.getDescription())
                ? instanceCfg.getDescription() : r.getDescription());
        Integer effectiveScan = instanceCfg != null && instanceCfg.getScanIntervalMin() != null
                ? instanceCfg.getScanIntervalMin() : r.getScanIntervalMin();
        vo.setScanIntervalMin(effectiveScan);
        vo.setScanIntervalSource(instanceCfg != null && instanceCfg.getScanIntervalMin() != null
                ? "USER_OVERRIDE" : r.getScanIntervalSource());
        IntervalCapability capability = computeIntervalCapability(metricCodes);
        vo.setMetricSamplingMaxIntervalMin(capability.metricSamplingMaxIntervalMin);
        vo.setMinAllowedIntervalMin(capability.minAllowedIntervalMin);
        vo.setInstanceEnabled(instanceEnabled);
        vo.setCreatedBy(r.getCreatedBy());
        vo.setCreatedAt(r.getCreatedAt());
        vo.setUpdatedAt(r.getUpdatedAt());

        // 展平 conditionConfig：模板打底 + 实例覆盖逐字段叠加。
        // 内置 SQL 规则的实例覆盖仅含 operator/threshold 等阈值字段，整包替换会丢失模板的 customSql。
        Map<String, Object> cond = overlayConfig(r.getConditionConfig(),
                instanceCfg != null ? instanceCfg.getConditionConfig() : null);
        if (cond != null) {
            vo.setOperator(getFrom(cond, "operator"));
            Object th = cond.get("threshold");
            if (th instanceof Number n) vo.setThreshold(n.doubleValue());
            vo.setUnit(getFrom(cond, "unit"));
            Object dur = cond.get("duration");
            if (dur instanceof Number n) vo.setDuration(n.intValue());
            vo.setCustomSql(getFrom(cond, "customSql"));
            vo.setResultMode(getFrom(cond, "resultMode"));
            vo.setSqlReturnField(getFrom(cond, "sqlReturnField"));
            vo.setEntityColumn(getFrom(cond, "entityColumn"));
            vo.setValueColumn(getFrom(cond, "valueColumn"));
            vo.setDisplayTemplate(getFrom(cond, "displayTemplate"));
            vo.setConditionDisplay(getFrom(cond, "displayText"));
        }
        vo.setConditionLocked(isConditionLocked(cond));
        vo.setDataSource(resolveDataSource(cond));

        // 展平 recoveryConfig（同样按模板打底 + 实例覆盖叠加）
        Map<String, Object> rec = overlayConfig(r.getRecoveryConfig(),
                instanceCfg != null ? instanceCfg.getRecoveryConfig() : null);
        if (rec != null) {
            vo.setRecoveryOperator(getFrom(rec, "operator"));
            Object th = rec.get("threshold");
            if (th instanceof Number n) vo.setRecoveryThreshold(n.doubleValue());
            Object dur = rec.get("duration");
            if (dur instanceof Number n) vo.setRecoveryDuration(n.intValue());
            vo.setRecoveryDisplay(getFrom(rec, "displayText"));
        }

        // 展平 notificationConfig
        Map<String, Object> notify = instanceCfg != null && instanceCfg.getNotificationConfig() != null
                ? instanceCfg.getNotificationConfig() : r.getNotificationConfig();
        if (notify != null) {
            vo.setNotifyOnTrigger((Boolean) notify.get("notifyOnTrigger"));
            vo.setNotifyOnRecovery((Boolean) notify.get("notifyOnRecovery"));
            vo.setChannelEmail((Boolean) notify.get("channelEmail"));
            vo.setChannelSms((Boolean) notify.get("channelSms"));
            vo.setChannelWebhook((Boolean) notify.get("channelWebhook"));
            vo.setChannelDingtalk((Boolean) notify.get("channelDingtalk"));
            vo.setChannelWecom((Boolean) notify.get("channelWecom"));
            vo.setChannelFeishu((Boolean) notify.get("channelFeishu"));
            Object sp = notify.get("silencePeriod");
            if (sp instanceof Number n) vo.setSilencePeriod(n.intValue());
        }

        vo.setTriggerCount(triggerCount);
        vo.setLastTriggerAt(lastTriggerAt);
        return vo;
    }

    private String normalizeDisplayTemplate(String displayTemplate) {
        return StringUtils.hasText(displayTemplate) ? displayTemplate.trim() : DEFAULT_MULTI_DISPLAY_TEMPLATE;
    }

    private record IntervalCapability(int systemMinIntervalMin,
                                      int metricSamplingMaxIntervalMin,
                                      int minAllowedIntervalMin) {
    }
}
