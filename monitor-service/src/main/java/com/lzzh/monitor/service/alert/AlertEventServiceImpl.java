package com.lzzh.monitor.service.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.AlertDeadLetterPageRequest;
import com.lzzh.monitor.api.request.AlertEventCountRequest;
import com.lzzh.monitor.api.request.AlertEventPageRequest;
import com.lzzh.monitor.api.response.AlertEventDrilldownVo;
import com.lzzh.monitor.api.response.AlertEventOperateLogVo;
import com.lzzh.monitor.api.response.AlertEventVo;
import com.lzzh.monitor.api.response.AlertNotifyRecordVo;
import com.lzzh.monitor.common.constant.Constants;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.*;
import com.lzzh.monitor.dao.mapper.*;
import com.lzzh.monitor.service.datascope.DataScope;
import com.lzzh.monitor.service.datascope.DataScopeService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 告警事件查询与处置服务实现。
 *
 * <p>状态机采用单向流转（详见 {@link AlertEventService} 类注释），每次实际发生的流转都会写入
 * {@code alert_event_operate_log} 一条审计记录，同时把备注写入 {@code alert_event.last_remark}
 * 便于列表页直接展示"最近处置说明"。
 */
@Service
public class AlertEventServiceImpl implements AlertEventService {

    private static final Logger log = LoggerFactory.getLogger(AlertEventServiceImpl.class);

    // 状态集合特意保留包内可见（非 private），便于单元测试直接断言"单向流转规则"这一核心不变量。
    static final List<String> ACTIVE_STATUSES = List.of("pending", "confirmed", "handling");
    /** 确认：仅允许从待处理发起，避免"受理中又被确认打回"的来回跳转。 */
    static final List<String> CONFIRMABLE_STATUSES = List.of("pending");
    /** 受理：待处理或已确认均可进入处理中。 */
    static final List<String> HANDLINGABLE_STATUSES = List.of("pending", "confirmed");
    /** 关闭：已确认或处理中均可关闭，但两者互不可逆转（confirmed/handling 之间不再互转）。 */
    static final List<String> CLOSEABLE_STATUSES = List.of("confirmed", "handling");
    /** 人工关闭后短期抑制同 dedupKey 重开，避免指标仍异常时下一轮立即建单。 */
    private static final int DEFAULT_CLOSE_SUPPRESS_HOURS = 2;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int REMARK_MAX_LEN = 500;

    @Resource
    private AlertEventMapper alertEventMapper;
    @Resource
    private AlertNotifyRecordMapper notifyRecordMapper;
    @Resource
    private AlertEventOperateLogMapper operateLogMapper;
    @Resource
    private AlertEvaluateWindowMapper evaluateWindowMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private AlertRuleMapper alertRuleMapper;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private MonitorScenarioMapper scenarioMapper;
    @Resource
    private KnowledgeArticleMapper knowledgeArticleMapper;
    @Resource
    private MetricDefinitionMapper metricDefinitionMapper;
    @Resource
    private AlertDrilldownProfileService drilldownProfileService;
    @Resource
    private DbInstanceMapper dbInstanceMapper;
    @Resource
    private DatabaseTypeMapper databaseTypeMapper;

    @Override
    public PageResult<AlertEventVo> page(AlertEventPageRequest req) {
        if (req == null) {
            req = new AlertEventPageRequest();
        }
        DataScope scope = dataScopeService.currentScope();
        if (scope.isEmpty()) {
            return PageResult.of(List.of(), 0);
        }
        LambdaQueryWrapper<AlertEvent> wrapper = buildWrapper(
                req.getInstanceId(),
                req.getRuleLevel(),
                req.getStatuses(),
                scope
        )
                .eq(StringUtils.hasText(req.getEventSource()), AlertEvent::getEventSource, req.getEventSource())
                .eq(StringUtils.hasText(req.getScenarioCode()), AlertEvent::getScenarioCode, req.getScenarioCode())
                .orderByDesc(AlertEvent::getTriggerTime);

        Page<AlertEvent> page = alertEventMapper.selectPage(
                new Page<>(req.getPageNum(), req.getPageSize()), wrapper);

        Map<Long, String> userNameMap = loadUserNameMap(page.getRecords());
        Set<Long> booleanRuleIds = loadBooleanRuleIds(page.getRecords());
        Map<String, List<Map<String, Object>>> knowledgeMap = loadScenarioKnowledge(page.getRecords());
        List<AlertEventVo> vos = page.getRecords().stream()
                .map(e -> toVo(e, userNameMap, booleanRuleIds, knowledgeMap)).toList();
        return PageResult.of(vos, page.getTotal());
    }

    @Override
    public long count(AlertEventCountRequest req) {
        if (req == null) {
            req = new AlertEventCountRequest();
        }
        DataScope scope = dataScopeService.currentScope();
        if (scope.isEmpty()) {
            return 0;
        }
        return alertEventMapper.selectCount(buildWrapper(
                req.getInstanceId(),
                req.getRuleLevel(),
                req.getStatuses(),
                scope
        ));
    }

    @Override
    public AlertEventDrilldownVo drilldown(Long eventId) {
        AlertEvent e = alertEventMapper.selectById(eventId);
        if (e == null) {
            throw new BusinessException("告警事件不存在");
        }
        if (!dataScopeService.currentScope().allows(e.getInstanceId())) {
            throw new BusinessException("无权查看该实例的告警事件");
        }

        AlertEventDrilldownVo vo = new AlertEventDrilldownVo();
        vo.setEvent(toVo(e, loadUserNameMap(List.of(e)), loadBooleanRuleIds(List.of(e)),
                loadScenarioKnowledge(List.of(e))));
        if (e.getRecoveryTime() != null) {
            vo.setRecoveryTime(e.getRecoveryTime().format(FMT));
        }

        // 规则元数据：规则可能已被删除（rule_id 置空），此时仅返回事件本身，前端按通用画像降级
        AlertRule rule = e.getRuleId() == null ? null : alertRuleMapper.selectById(e.getRuleId());
        if (rule != null) {
            vo.setRuleCode(rule.getRuleCode());
            vo.setRuleDescription(rule.getDescription());
            vo.setMetricCode(rule.getMetricName());
            if (rule.getConditionConfig() != null) {
                Object op = rule.getConditionConfig().get("operator");
                vo.setOperator(op == null ? null : String.valueOf(op));
            }
        } else if (isConnectionFailureEvent(e)) {
            // 系统内置连接失败事件无规则行，给前端一个稳定的画像匹配键
            vo.setMetricCode(Constants.SYSTEM_RULE_CONNECTION_FAILURE);
        }

        // 指标定义补充中文名与单位（定义缺失时前端回退 metricCode）
        if (StringUtils.hasText(vo.getMetricCode())) {
            MetricDefinition def = metricDefinitionMapper.selectOne(
                    new LambdaQueryWrapper<MetricDefinition>()
                            .eq(MetricDefinition::getMetricCode, vo.getMetricCode())
                            .last("LIMIT 1"));
            if (def != null) {
                vo.setMetricLabel(def.getMetricName());
                vo.setUnit(def.getUnit());
            }
        }

        // 匹配告警类型画像（数据库配置，exact > 长前缀 > generic 兜底）；
        // 场景综合事件无单一触发指标，以 scenario_code 作为匹配键（V128 已为内置场景配置前缀规则）
        String profileKey = "scenario".equals(e.getEventSource()) && StringUtils.hasText(e.getScenarioCode())
                ? e.getScenarioCode()
                : vo.getMetricCode();
        vo.setProfile(drilldownProfileService.match(profileKey, resolveDbType(e.getInstanceId())));
        return vo;
    }

    private String resolveDbType(Long instanceId) {
        DbInstance instance = instanceId == null ? null : dbInstanceMapper.selectById(instanceId);
        DatabaseType type = instance == null || instance.getDbTypeId() == null
                ? null : databaseTypeMapper.selectById(instance.getDbTypeId());
        return type == null ? null : type.getCode();
    }

    @Override
    @Transactional
    public int confirm(List<Long> ids, Long operatorId, String assignee, String remark) {
        List<AlertEvent> matched = selectTransitionable(ids, CONFIRMABLE_STATUSES);
        if (matched.isEmpty()) {
            return 0;
        }
        String normalizedRemark = normalizeRemark(remark);
        OffsetDateTime now = OffsetDateTime.now();
        // 逐事件带 status 守卫更新：查询到更新之间事件可能被评估线程改为 recovered 等状态，
        // 审计流水只对实际更新成功的事件写入，保证"流水条数 == 真实流转数"（下同）
        List<AlertEvent> succeeded = updateEachWithGuard(matched, CONFIRMABLE_STATUSES, e ->
                new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getId, e.getId())
                        .in(AlertEvent::getStatus, CONFIRMABLE_STATUSES)
                        .set(AlertEvent::getStatus, "confirmed")
                        .set(AlertEvent::getAssignee, assignee)
                        .set(AlertEvent::getConfirmUserId, operatorId)
                        .set(AlertEvent::getLastRemark, normalizedRemark)
                        .set(AlertEvent::getUpdatedAt, now)
        );
        writeOperateLogs(succeeded, "confirm", "confirmed", operatorId, assignee, normalizedRemark, now);
        return succeeded.size();
    }

    @Override
    @Transactional
    public int handling(List<Long> ids, Long operatorId, String assignee, String remark) {
        List<AlertEvent> matched = selectTransitionable(ids, HANDLINGABLE_STATUSES);
        if (matched.isEmpty()) {
            return 0;
        }
        String normalizedRemark = normalizeRemark(remark);
        OffsetDateTime now = OffsetDateTime.now();
        List<AlertEvent> succeeded = updateEachWithGuard(matched, HANDLINGABLE_STATUSES, e ->
                new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getId, e.getId())
                        .in(AlertEvent::getStatus, HANDLINGABLE_STATUSES)
                        .set(AlertEvent::getStatus, "handling")
                        .set(AlertEvent::getAssignee, assignee)
                        .set(AlertEvent::getLastRemark, normalizedRemark)
                        .set(AlertEvent::getUpdatedAt, now)
        );
        writeOperateLogs(succeeded, "handling", "handling", operatorId, assignee, normalizedRemark, now);
        return succeeded.size();
    }

    @Override
    @Transactional
    public int silence(List<Long> ids, Long operatorId, String assignee, Integer silenceHours, String remark) {
        if (silenceHours == null || silenceHours < 1) {
            throw new IllegalArgumentException("静默时长必须大于0小时");
        }
        List<AlertEvent> matched = selectTransitionable(ids, ACTIVE_STATUSES);
        if (matched.isEmpty()) {
            return 0;
        }
        String normalizedRemark = normalizeRemark(remark);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime until = now.plusHours(silenceHours);
        List<AlertEvent> succeeded = updateEachWithGuard(matched, ACTIVE_STATUSES, e ->
                new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getId, e.getId())
                        .in(AlertEvent::getStatus, ACTIVE_STATUSES)
                        .set(AlertEvent::getStatus, "ignored")
                        .set(AlertEvent::getAssignee, assignee)
                        .set(AlertEvent::getSilenceUserId, operatorId)
                        .set(AlertEvent::getSilenceUntilTime, until)
                        // 静默不写 recovery_time：事件并未恢复，恢复时间只由评估器/自愈路径写入
                        .set(AlertEvent::getLastRemark, normalizedRemark)
                        .set(AlertEvent::getUpdatedAt, now)
        );
        writeOperateLogs(succeeded, "silence", "ignored", operatorId, assignee, normalizedRemark, now);
        cleanupEvaluateWindows(succeeded);
        return succeeded.size();
    }

    @Override
    @Transactional
    public int close(List<Long> ids, Long operatorId, String assignee, String remark) {
        List<AlertEvent> matched = selectTransitionable(ids, CLOSEABLE_STATUSES);
        if (matched.isEmpty()) {
            return 0;
        }
        String normalizedRemark = normalizeRemark(remark);
        OffsetDateTime now = OffsetDateTime.now();
        List<AlertEvent> succeeded = updateEachWithGuard(matched, CLOSEABLE_STATUSES, e ->
                new LambdaUpdateWrapper<AlertEvent>()
                        .eq(AlertEvent::getId, e.getId())
                        .in(AlertEvent::getStatus, CLOSEABLE_STATUSES)
                        .set(AlertEvent::getStatus, "closed")
                        .set(AlertEvent::getAssignee, assignee)
                        .set(AlertEvent::getCloseUserId, operatorId)
                        .set(AlertEvent::getSilenceUntilTime, now.plusHours(DEFAULT_CLOSE_SUPPRESS_HOURS))
                        .set(AlertEvent::getLastRemark, normalizedRemark)
                        .set(AlertEvent::getUpdatedAt, now)
        );
        writeOperateLogs(succeeded, "close", "closed", operatorId, assignee, normalizedRemark, now);
        cleanupEvaluateWindows(succeeded);
        return succeeded.size();
    }

    /**
     * 人工处置（静默/关闭）后清理对应 dedupKey 的持续窗口。
     * <p>不清理会导致：抑制窗口过后指标仍异常时，残留的 trigger 窗口使"持续时长"从
     * 处置前的旧首次命中时间起算，新事件绕过 duration 判定被立即建单。
     */
    private void cleanupEvaluateWindows(List<AlertEvent> matched) {
        List<String> keys = matched.stream()
                .map(AlertEvent::getDedupKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        evaluateWindowMapper.deleteByDedupKeys(keys);
    }

    @Override
    public List<AlertNotifyRecordVo> listNotifyRecords(Long eventId) {
        if (eventId == null || !isEventAccessible(eventId)) {
            return List.of();
        }
        return notifyRecordMapper.selectList(new LambdaQueryWrapper<AlertNotifyRecord>()
                        .eq(AlertNotifyRecord::getEventId, eventId)
                        .orderByDesc(AlertNotifyRecord::getId))
                .stream()
                .map(this::toNotifyVo)
                .toList();
    }

    @Override
    public List<AlertEventOperateLogVo> listOperateLogs(Long eventId) {
        if (eventId == null || !isEventAccessible(eventId)) {
            return List.of();
        }
        List<AlertEventOperateLog> logs = operateLogMapper.selectList(
                new LambdaQueryWrapper<AlertEventOperateLog>()
                        .eq(AlertEventOperateLog::getEventId, eventId)
                        .orderByDesc(AlertEventOperateLog::getId));
        return logs.stream().map(this::toOperateLogVo).toList();
    }

    @Override
    public PageResult<AlertNotifyRecordVo> pageDeadLetters(AlertDeadLetterPageRequest req) {
        if (req == null) {
            req = new AlertDeadLetterPageRequest();
        }
        DataScope scope = dataScopeService.currentScope();
        if (scope.isEmpty()) {
            return PageResult.of(List.of(), 0);
        }
        LambdaQueryWrapper<AlertNotifyRecord> wrapper = new LambdaQueryWrapper<AlertNotifyRecord>()
                .eq(AlertNotifyRecord::getStatus, "dead")
                .eq(StringUtils.hasText(req.getChannel()), AlertNotifyRecord::getChannel, req.getChannel())
                .orderByDesc(AlertNotifyRecord::getId);
        // 通知记录本身不带 instanceId，数据范围与实例过滤通过 event_id 子查询回落到 alert_event 施加。
        String eventFilter = buildAccessibleEventFilter(req.getInstanceId(), scope);
        if (eventFilter != null) {
            wrapper.inSql(AlertNotifyRecord::getEventId, eventFilter);
        }
        Page<AlertNotifyRecord> page = notifyRecordMapper.selectPage(
                new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
        List<AlertNotifyRecordVo> vos = page.getRecords().stream().map(this::toNotifyVo).toList();
        return PageResult.of(vos, page.getTotal());
    }

    @Override
    public boolean resendDeadLetter(Long recordId, Long operatorId) {
        if (recordId == null) {
            return false;
        }
        AlertNotifyRecord record = notifyRecordMapper.selectById(recordId);
        if (record == null || !"dead".equals(record.getStatus()) || !isEventAccessible(record.getEventId())) {
            return false;
        }
        int updated = notifyRecordMapper.resetDeadForResend(recordId);
        if (updated > 0) {
            log.info("人工重发死信通知 recordId={} eventCode={} channel={} target={} operatorId={}",
                    recordId, record.getEventCode(), record.getChannel(), record.getTarget(), operatorId);
        }
        return updated > 0;
    }

    /**
     * 构造"当前用户可访问 + 可选实例过滤"的 event_id 子查询 SQL；无需过滤（无限权限且未指定实例）时返回 null。
     * 入参 instanceId 为强类型 Long、scope.instanceIds() 为服务端计算值，均非用户可注入字符串，拼接安全。
     */
    private String buildAccessibleEventFilter(Long instanceId, DataScope scope) {
        boolean restricted = !scope.isUnrestricted();
        if (instanceId == null && !restricted) {
            return null;
        }
        StringBuilder sub = new StringBuilder("SELECT id FROM alert_event WHERE 1 = 1");
        if (instanceId != null) {
            sub.append(" AND instance_id = ").append(instanceId);
        }
        if (restricted) {
            String ids = scope.instanceIds().stream().map(String::valueOf)
                    .collect(Collectors.joining(","));
            sub.append(" AND instance_id IN (").append(ids).append(")");
        }
        return sub.toString();
    }

    // ── 状态机流转辅助 ────────────────────────────────────────────────────────

    /**
     * 查出本批 ID 中"当前状态确实允许发起该操作"的事件（用于二次校验 + 生成操作流水的 fromStatus 快照）。
     * 不在允许状态集合内的 ID 会被静默忽略，不抛异常——批量操作场景下部分事件状态已变化是常见情况。
     *
     * <p>同时叠加数据范围过滤：超出当前用户可见实例范围的事件同样被静默排除，
     * 避免越权处置不属于自己负责的实例告警。
     */
    private List<AlertEvent> selectTransitionable(List<Long> ids, List<String> allowedFromStatuses) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        DataScope scope = dataScopeService.currentScope();
        if (scope.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>()
                .select(AlertEvent::getId, AlertEvent::getEventCode, AlertEvent::getStatus, AlertEvent::getDedupKey)
                .in(AlertEvent::getId, ids)
                .in(AlertEvent::getStatus, allowedFromStatuses);
        if (!scope.isUnrestricted()) {
            wrapper.in(AlertEvent::getInstanceId, scope.instanceIds());
        }
        return alertEventMapper.selectList(wrapper);
    }

    /** 事件所属实例是否在当前用户数据范围内；事件不存在时视为不可访问（避免枚举探测）。 */
    private boolean isEventAccessible(Long eventId) {
        DataScope scope = dataScopeService.currentScope();
        if (scope.isUnrestricted()) {
            return true;
        }
        if (scope.isEmpty()) {
            return false;
        }
        AlertEvent event = alertEventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                .select(AlertEvent::getId, AlertEvent::getInstanceId)
                .eq(AlertEvent::getId, eventId)
                .last("LIMIT 1"));
        return event != null && scope.allows(event.getInstanceId());
    }

    /**
     * 逐事件执行带 status 守卫的 UPDATE，返回实际更新成功的事件列表。
     * <p>批量 UPDATE + IN 无法得知具体哪些行命中守卫，会造成审计流水与真实流转不一致；
     * 处置操作批量规模很小（页面单选/多选），逐条 UPDATE 的开销可忽略。
     */
    private List<AlertEvent> updateEachWithGuard(List<AlertEvent> matched,
                                                 List<String> allowedFromStatuses,
                                                 java.util.function.Function<AlertEvent, LambdaUpdateWrapper<AlertEvent>> wrapperFn) {
        List<AlertEvent> succeeded = new java.util.ArrayList<>(matched.size());
        for (AlertEvent e : matched) {
            int updated = alertEventMapper.update(null, wrapperFn.apply(e));
            if (updated > 0) {
                succeeded.add(e);
            } else {
                log.info("事件 {} 状态已变化（不在 {} 中），跳过本次处置", e.getId(), allowedFromStatuses);
            }
        }
        return succeeded;
    }

    private void writeOperateLogs(List<AlertEvent> matched, String operateType, String toStatus,
                                  Long operatorId, String operatorName, String remark, OffsetDateTime now) {
        for (AlertEvent e : matched) {
            AlertEventOperateLog log = new AlertEventOperateLog();
            log.setEventId(e.getId());
            log.setEventCode(e.getEventCode());
            log.setOperateType(operateType);
            log.setFromStatus(e.getStatus());
            log.setToStatus(toStatus);
            log.setOperatorId(operatorId);
            log.setOperatorName(operatorName);
            log.setRemark(remark);
            log.setCreatedAt(now);
            operateLogMapper.insert(log);
        }
    }

    private static String normalizeRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String trimmed = remark.trim();
        return trimmed.length() > REMARK_MAX_LEN ? trimmed.substring(0, REMARK_MAX_LEN) : trimmed;
    }

    // ── 查询辅助 ─────────────────────────────────────────────────────────────

    private LambdaQueryWrapper<AlertEvent> buildWrapper(
            Long instanceId,
            String ruleLevel,
            List<String> statuses,
            DataScope scope
    ) {
        List<String> finalStatuses = (statuses == null || statuses.isEmpty())
                ? ACTIVE_STATUSES : statuses;
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>()
                .in(AlertEvent::getStatus, finalStatuses)
                .eq(instanceId != null, AlertEvent::getInstanceId, instanceId)
                .eq(ruleLevel != null && !ruleLevel.isBlank(), AlertEvent::getRuleLevel, ruleLevel);
        if (scope != null && !scope.isUnrestricted()) {
            wrapper.in(AlertEvent::getInstanceId, scope.instanceIds());
        }
        return wrapper;
    }

    private Map<Long, String> loadUserNameMap(List<AlertEvent> events) {
        Set<Long> ids = new HashSet<>();
        for (AlertEvent e : events) {
            if (e.getConfirmUserId() != null) ids.add(e.getConfirmUserId());
            if (e.getSilenceUserId() != null) ids.add(e.getSilenceUserId());
            if (e.getCloseUserId() != null) ids.add(e.getCloseUserId());
        }
        if (ids.isEmpty()) return Map.of();
        return sysUserMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(
                        SysUser::getId,
                        u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername(),
                        (a, b) -> a
                ));
    }

    /**
     * 找出本页事件中属于布尔型规则（conditionConfig.conditionType = boolean）的规则 ID：
     * 这类事件的触发值/阈值是 0/1，页面应展示状态化文案而非数值。
     */
    private Set<Long> loadBooleanRuleIds(List<AlertEvent> events) {
        Set<Long> ruleIds = events.stream()
                .map(AlertEvent::getRuleId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ruleIds.isEmpty()) {
            return Set.of();
        }
        return alertRuleMapper.selectByIds(ruleIds).stream()
                .filter(r -> r.getConditionConfig() != null
                        && "boolean".equals(r.getConditionConfig().get("conditionType")))
                .map(AlertRule::getId)
                .collect(Collectors.toSet());
    }

    /** 是否系统内置连接失败事件：dedup_key = system.connection_failure:{instanceId}。 */
    private static boolean isConnectionFailureEvent(AlertEvent e) {
        return e.getDedupKey() != null
                && e.getDedupKey().startsWith(Constants.SYSTEM_RULE_CONNECTION_FAILURE + ":");
    }

    /**
     * 本页场景来源事件的关联知识库文章：scenario_code → [{id,title}]。
     * <p>按场景的 knowledge_article_ids 反查文章标题，页面详情抽屉直接展示可点击链接。
     */
    private Map<String, List<Map<String, Object>>> loadScenarioKnowledge(List<AlertEvent> events) {
        Set<String> scenarioCodes = events.stream()
                .map(AlertEvent::getScenarioCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (scenarioCodes.isEmpty()) {
            return Map.of();
        }
        List<MonitorScenario> scenarios = scenarioMapper.selectList(new LambdaQueryWrapper<MonitorScenario>()
                .in(MonitorScenario::getScenarioCode, scenarioCodes)
                .select(MonitorScenario::getScenarioCode, MonitorScenario::getKnowledgeArticleIds));
        Set<Long> articleIds = scenarios.stream()
                .filter(s -> s.getKnowledgeArticleIds() != null)
                .flatMap(s -> s.getKnowledgeArticleIds().stream())
                .collect(Collectors.toSet());
        Map<Long, String> titleMap = articleIds.isEmpty() ? Map.of()
                : knowledgeArticleMapper.selectByIds(articleIds).stream()
                        .collect(Collectors.toMap(KnowledgeArticle::getId, KnowledgeArticle::getTitle, (a, b) -> a));
        Map<String, List<Map<String, Object>>> result = new java.util.HashMap<>();
        for (MonitorScenario s : scenarios) {
            List<Map<String, Object>> articles = s.getKnowledgeArticleIds() == null ? List.of()
                    : s.getKnowledgeArticleIds().stream()
                            .filter(titleMap::containsKey)
                            .map(id -> Map.<String, Object>of("id", id, "title", titleMap.get(id)))
                            .toList();
            result.put(s.getScenarioCode(), articles);
        }
        return result;
    }

    private AlertEventVo toVo(AlertEvent e, Map<Long, String> userNameMap, Set<Long> booleanRuleIds,
                              Map<String, List<Map<String, Object>>> knowledgeMap) {
        AlertEventVo vo = new AlertEventVo();
        // 系统内置连接失败事件按 dedup_key 前缀识别（rule_id 为 NULL 不可靠：
        // 规则被删除后历史事件的 rule_id 同样会失去关联），
        // 其触发值是重试次数（恢复时为 0），与布尔型规则一样按状态化文案展示
        vo.setBooleanCondition(isConnectionFailureEvent(e)
                || (e.getRuleId() != null && booleanRuleIds.contains(e.getRuleId())));
        vo.setId(e.getId());
        vo.setEventCode(e.getEventCode());
        vo.setRuleId(e.getRuleId());
        vo.setRuleName(e.getRuleName());
        vo.setRuleLevel(e.getRuleLevel());
        vo.setInstanceId(e.getInstanceId());
        vo.setInstanceName(e.getInstanceName());
        vo.setTriggerValue(e.getTriggerValue());
        vo.setThresholdValue(e.getThresholdValue());
        vo.setAlertMessage(e.getAlertMessage());
        vo.setDimensionKey(e.getDimensionKey());
        vo.setTriggerCount(e.getTriggerCount());
        vo.setStatus(e.getStatus());
        vo.setAssignee(e.getAssignee());
        vo.setLastRemark(e.getLastRemark());
        vo.setConfirmUserId(e.getConfirmUserId());
        vo.setSilenceUserId(e.getSilenceUserId());
        vo.setCloseUserId(e.getCloseUserId());
        vo.setConfirmUserName(resolveUserName(userNameMap, e.getConfirmUserId()));
        vo.setSilenceUserName(resolveUserName(userNameMap, e.getSilenceUserId()));
        vo.setCloseUserName(resolveUserName(userNameMap, e.getCloseUserId()));
        if (e.getSilenceUntilTime() != null) {
            vo.setSilenceUntilTime(e.getSilenceUntilTime().format(FMT));
        }
        vo.setEventSource(e.getEventSource());
        vo.setScenarioCode(e.getScenarioCode());
        vo.setSignalsSnapshot(e.getSignalsSnapshot());
        vo.setBlockingChainSnapshot(e.getBlockingChainSnapshot());
        if (StringUtils.hasText(e.getScenarioCode())) {
            vo.setKnowledgeArticles(knowledgeMap.getOrDefault(e.getScenarioCode(), List.of()));
        }
        vo.setEvalState(e.getEvalState());
        vo.setEvalMessage(e.getEvalMessage());
        if (e.getLastEvalTime() != null) {
            vo.setLastEvalTime(e.getLastEvalTime().format(FMT));
        }
        if (e.getTriggerTime() != null) {
            vo.setTriggerTime(e.getTriggerTime().format(FMT));
        }
        if (e.getLastTriggerTime() != null) {
            vo.setLastTriggerTime(e.getLastTriggerTime().format(FMT));
        }
        // 持续时间：活跃态到当前；已恢复到 recoveryTime；已静默/已关闭无恢复时间，
        // 取 updatedAt（即处置时刻）近似终点，避免终态事件的持续时间随当前时间无限增长。
        if (e.getTriggerTime() != null) {
            OffsetDateTime end = switch (e.getStatus()) {
                case "recovered" -> e.getRecoveryTime() != null ? e.getRecoveryTime() : OffsetDateTime.now();
                case "ignored", "closed" -> e.getUpdatedAt() != null ? e.getUpdatedAt() : OffsetDateTime.now();
                default -> OffsetDateTime.now();
            };
            long secs = ChronoUnit.SECONDS.between(e.getTriggerTime(), end);
            vo.setDurationSeconds(Math.max(0, secs));
        }
        return vo;
    }

    private AlertNotifyRecordVo toNotifyVo(AlertNotifyRecord record) {
        AlertNotifyRecordVo vo = new AlertNotifyRecordVo();
        vo.setId(record.getId());
        vo.setEventId(record.getEventId());
        vo.setEventCode(record.getEventCode());
        vo.setRuleCode(record.getRuleCode());
        vo.setNotifyKind(record.getNotifyKind());
        vo.setChannel(record.getChannel());
        vo.setProvider(record.getProvider());
        vo.setTarget(record.getTarget());
        vo.setStatus(record.getStatus());
        vo.setResponseCode(record.getResponseCode());
        vo.setResponseBody(record.getResponseBody());
        vo.setErrorMessage(record.getErrorMessage());
        vo.setRetryCount(record.getRetryCount());
        vo.setMaxRetry(record.getMaxRetry());
        if (record.getNextRetryTime() != null) {
            vo.setNextRetryTime(record.getNextRetryTime().format(FMT));
        }
        if (record.getSentAt() != null) {
            vo.setSentAt(record.getSentAt().format(FMT));
        }
        if (record.getCreatedAt() != null) {
            vo.setCreatedAt(record.getCreatedAt().format(FMT));
        }
        if (record.getUpdatedAt() != null) {
            vo.setUpdatedAt(record.getUpdatedAt().format(FMT));
        }
        return vo;
    }

    private AlertEventOperateLogVo toOperateLogVo(AlertEventOperateLog log) {
        AlertEventOperateLogVo vo = new AlertEventOperateLogVo();
        vo.setId(log.getId());
        vo.setEventId(log.getEventId());
        vo.setOperateType(log.getOperateType());
        vo.setFromStatus(log.getFromStatus());
        vo.setToStatus(log.getToStatus());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setRemark(log.getRemark());
        if (log.getCreatedAt() != null) {
            vo.setCreatedAt(log.getCreatedAt().format(FMT));
        }
        return vo;
    }

    private String resolveUserName(Map<Long, String> userNameMap, Long userId) {
        if (userId == null) {
            return null;
        }
        return userNameMap.get(userId);
    }
}
