package com.lzzh.monitor.service.instance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.ConnectionTestRequest;
import com.lzzh.monitor.api.request.InstancePageRequest;
import com.lzzh.monitor.api.request.InstanceRequest;
import com.lzzh.monitor.api.response.*;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.security.PasswordCipher;
import com.lzzh.monitor.dao.entity.*;
import com.lzzh.monitor.dao.mapper.*;
import com.lzzh.monitor.service.convert.InstanceConverter;
import com.lzzh.monitor.service.datascope.DataScope;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InstanceServiceImpl implements InstanceService {

    private static final Logger log = LoggerFactory.getLogger(InstanceServiceImpl.class);

    @Resource
    private DbInstanceMapper mapper;
    @Resource
    private DatabaseTypeMapper databaseTypeMapper;
    @Resource
    private DatabaseVersionMapper databaseVersionMapper;
    @Resource
    private PasswordCipher passwordCipher;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private AlertEventMapper alertEventMapper;
    @Resource
    private InstanceGroupMapper instanceGroupMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private HostMapper hostMapper;
    @Resource
    private InstanceDataCleanupMapper instanceDataCleanupMapper;

    /** 主机查找表（hostId → Host），供列表/详情解析 hostName / hostOsType。 */
    private Map<Long, Host> loadHostMap(List<DbInstance> instances) {
        Set<Long> hostIds = instances.stream()
                .map(DbInstance::getHostId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (hostIds.isEmpty()) {
            return Map.of();
        }
        return hostMapper.selectByIds(hostIds).stream()
                .collect(Collectors.toMap(Host::getId, h -> h, (a, b) -> a));
    }

    private static void fillHostInfo(InstanceVo vo, DbInstance e, Map<Long, Host> hostMap) {
        Host host = e.getHostId() == null ? null : hostMap.get(e.getHostId());
        if (host != null) {
            vo.setHostName(host.getName());
            vo.setHostOsType(host.getOsType());
        }
    }

    // ── 查找表加载（每次调用时全量加载；数据量小，后续可加缓存）─────────────────────

    private Map<Long, DatabaseType> loadTypeMap() {
        return databaseTypeMapper.selectList(null).stream()
                .collect(Collectors.toMap(DatabaseType::getId, t -> t, (a, b) -> a));
    }

    private Map<Long, String> loadVersionMap() {
        return databaseVersionMapper.selectList(null).stream()
                .collect(Collectors.toMap(DatabaseVersion::getId, DatabaseVersion::getVersionCode, (a, b) -> a));
    }

    /** 从 DatabaseType 解析展示名（label 优先，无则用 code）。 */
    private static String resolveTypeName(DatabaseType dt) {
        if (dt == null) return null;
        return StringUtils.hasText(dt.getLabel()) ? dt.getLabel() : dt.getCode();
    }

    /** 校验类型/版本引用及归属关系，服务端不接受前端自行拼接的组合。 */
    private void validateDatabaseSelection(Long dbTypeId, Long dbVersionId) {
        if (dbTypeId == null || dbVersionId == null) {
            throw new BusinessException("数据库类型和版本不能为空");
        }
        DatabaseType type = databaseTypeMapper.selectById(dbTypeId);
        DatabaseVersion version = databaseVersionMapper.selectById(dbVersionId);
        if (type == null) {
            throw new BusinessException("数据库类型不存在: " + dbTypeId);
        }
        if (version == null) {
            throw new BusinessException("数据库版本不存在: " + dbVersionId);
        }
        if (!StringUtils.hasText(type.getCode()) || !type.getCode().equalsIgnoreCase(version.getDbType())) {
            throw new BusinessException("数据库版本不属于所选数据库类型");
        }
    }
    // ── 查询 ──────────────────────────────────────────────────────────────────

    @Override
    public PageResult<InstanceVo> page(InstancePageRequest query) {
        DataScope scope = dataScopeService.currentScope();
        if (scope.isEmpty()) {
            return PageResult.of(List.of(), 0);
        }
        Page<DbInstance> page = Pages.build(query);
        Page<DbInstance> result = mapper.selectPage(page, buildWrapper(query, scope));
        Map<Long, DatabaseType> typeMap = loadTypeMap();
        Map<Long, String> versionMap = loadVersionMap();
        Map<Long, Host> hostMap = loadHostMap(result.getRecords());
        return Pages.toResult(result).map(e -> {
            InstanceVo vo = InstanceConverter.toVo(
                    e,
                    resolveTypeName(typeMap.get(e.getDbTypeId())),
                    versionMap.get(e.getDbVersionId()));
            fillHostInfo(vo, e, hostMap);
            return vo;
        });
    }

    @Override
    public List<InstanceVo> listAll() {
        DataScope scope = dataScopeService.currentScope();
        if (scope.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DbInstance> wrapper = new LambdaQueryWrapper<DbInstance>().orderByDesc(DbInstance::getId);
        if (!scope.isUnrestricted()) {
            wrapper.in(DbInstance::getId, scope.instanceIds());
        }
        List<DbInstance> list = mapper.selectList(wrapper);
        Map<Long, DatabaseType> typeMap = loadTypeMap();
        Map<Long, String> versionMap = loadVersionMap();
        Map<Long, Host> hostMap = loadHostMap(list);
        return list.stream()
                .map(e -> {
                    InstanceVo vo = InstanceConverter.toVo(
                            e,
                            resolveTypeName(typeMap.get(e.getDbTypeId())),
                            versionMap.get(e.getDbVersionId()));
                    fillHostInfo(vo, e, hostMap);
                    return vo;
                })
                .toList();
    }

    private QueryWrapper<DbInstance> buildWrapper(InstancePageRequest q, DataScope scope) {
        QueryWrapper<DbInstance> qw = new QueryWrapper<>();
        if (q != null) {
            if (StringUtils.hasText(q.getKeyword())) {
                qw.and(w -> w.like("name", q.getKeyword()).or().like("host", q.getKeyword()));
            }
            if (q.getDbTypeId() != null) {
                qw.eq("db_type_id", q.getDbTypeId());
            }
            if (StringUtils.hasText(q.getStatus())) {
                qw.eq("status", q.getStatus());
            }
            if (q.getGroupId() != null) {
                qw.apply("group_ids @> {0}::jsonb", "[" + q.getGroupId() + "]");
            }
        }
        if (!scope.isUnrestricted()) {
            qw.in("id", scope.instanceIds());
        }
        qw.orderByDesc("id");
        return qw;
    }

    /** 校验实例是否在当前用户数据范围内，非法访问统一抛业务异常（403 语义）。 */
    private void checkAccessible(Long id) {
        DataScope scope = dataScopeService.currentScope();
        if (!scope.allows(id)) {
            throw new BusinessException("无权访问该实例: " + id);
        }
    }

    @Override
    public InstanceVo getById(Long id) {
        checkAccessible(id);
        DbInstance ins = mapper.selectById(id);
        if (ins == null) {
            throw new BusinessException("实例不存在: " + id);
        }
        Map<Long, DatabaseType> typeMap = loadTypeMap();
        Map<Long, String> versionMap = loadVersionMap();
        InstanceVo vo = InstanceConverter.toVo(
                ins,
                resolveTypeName(typeMap.get(ins.getDbTypeId())),
                versionMap.get(ins.getDbVersionId()));
        fillHostInfo(vo, ins, loadHostMap(List.of(ins)));
        return vo;
    }

    // ── 写操作 ────────────────────────────────────────────────────────────────

    @Override
    public Long create(InstanceRequest request) {
        validateDatabaseSelection(request.getDbTypeId(), request.getDbVersionId());
        DbInstance instance = InstanceConverter.toEntity(request);
        if (StringUtils.hasText(instance.getConnPassword())) {
            instance.setConnPassword(passwordCipher.encrypt(instance.getConnPassword()));
        }
        if (!StringUtils.hasText(instance.getInstanceCode())) {
            instance.setInstanceCode(UUID.randomUUID().toString());
        }
        mapper.insert(instance);
        return instance.getId();
    }

    @Override
    public void update(InstanceRequest request) {
        checkAccessible(request.getId());
        DbInstance existing = mapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException("实例不存在: " + request.getId());
        }
        if (!Objects.equals(existing.getDbTypeId(), request.getDbTypeId())
                || !Objects.equals(existing.getDbVersionId(), request.getDbVersionId())) {
            throw new BusinessException("实例创建后不允许修改数据库类型和版本");
        }
        validateDatabaseSelection(existing.getDbTypeId(), existing.getDbVersionId());
        DbInstance instance = InstanceConverter.toEntity(request);
        if (instance.getConnPassword() != null && instance.getConnPassword().isBlank()) {
            instance.setConnPassword(null);
        } else if (StringUtils.hasText(instance.getConnPassword())) {
            instance.setConnPassword(passwordCipher.encrypt(instance.getConnPassword()));
        }
        mapper.updateById(instance);
        // updateById 跳过 null 字段：主机关联需要支持"解除"（表单传空即置空），单独显式置空
        if (request.getHostId() == null) {
            mapper.update(null, new LambdaUpdateWrapper<DbInstance>()
                    .eq(DbInstance::getId, request.getId())
                    .set(DbInstance::getHostId, null));
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        checkAccessible(id);
        if (mapper.selectById(id) == null) {
            throw new BusinessException("实例不存在: " + id);
        }
        instanceDataCleanupMapper.deleteByInstanceId(id);
        mapper.deleteById(id);
    }

    @Override
    public void toggleStatus(Long id, String status) {
        checkAccessible(id);
        if (!"normal".equals(status) && !"paused".equals(status)) {
            throw new BusinessException("非法状态：" + status + "（接口仅允许 normal/paused；abnormal 由采集器自动维护）");
        }
        DbInstance ins = mapper.selectById(id);
        if (ins == null) {
            throw new BusinessException("实例不存在: " + id);
        }
        DbInstance update = new DbInstance();
        update.setId(id);
        update.setStatus(status);
        mapper.updateById(update);
    }

    // ── 舰队概况 ──────────────────────────────────────────────────────────────

    @Override
    public FleetSummaryVo summary() {
        List<DbInstance> all = mapper.selectList(null);

        int normal   = 0, abnormal = 0, paused = 0, healthSum = 0, healthCount = 0;
        int excellent = 0, good = 0, warning = 0, critical = 0, offline = 0;

        for (DbInstance ins : all) {
            String s = ins.getStatus();
            if ("normal".equals(s))        normal++;
            else if ("abnormal".equals(s)) abnormal++;
            else if ("paused".equals(s))   paused++;

            Integer h = ins.getHealth();
            if (h != null) {
                healthSum += h;
                healthCount++;
                if (h >= 90)      excellent++;
                else if (h >= 75) good++;
                else if (h >= 60) warning++;
                else if (h > 0)   critical++;
                else              offline++;
            } else {
                offline++;
            }
        }

        FleetSummaryVo vo = new FleetSummaryVo();
        vo.setTotal(all.size());
        vo.setNormal(normal);
        vo.setAbnormal(abnormal);
        vo.setPaused(paused);
        vo.setAvgHealth(healthCount == 0 ? 0 : healthSum / healthCount);

        List<FleetSummaryVo.LevelCount> dist = List.of(
                level("excellent", excellent),
                level("good",      good),
                level("warning",   warning),
                level("critical",  critical),
                level("offline",   offline)
        );
        vo.setDist(dist);
        return vo;
    }

    private static FleetSummaryVo.LevelCount level(String lv, int count) {
        FleetSummaryVo.LevelCount lc = new FleetSummaryVo.LevelCount();
        lc.setLevel(lv);
        lc.setCount(count);
        return lc;
    }

    // ── 首页全局总览（§11.1.2）─────────────────────────────────────────────────

    /** 未恢复（活跃）事件状态集合，与告警事件生命周期口径一致。 */
    private static final List<String> ACTIVE_EVENT_STATUSES = List.of("pending", "confirmed", "handling");

    /** 健康维度展示顺序与中文名（与 HealthScoreService 五维口径一致）。 */
    private static final LinkedHashMap<String, String> DIM_LABELS = new LinkedHashMap<>();
    static {
        DIM_LABELS.put("availability", "可用性");
        DIM_LABELS.put("performance", "性能");
        DIM_LABELS.put("stability", "稳定性");
        DIM_LABELS.put("capacity", "容量");
        DIM_LABELS.put("security", "安全配置");
    }

    @Override
    public FleetOverviewVo fleetOverview() {
        FleetOverviewVo vo = new FleetOverviewVo();
        vo.setDims(List.of());
        vo.setDbTypes(List.of());
        vo.setTopRisk(List.of());
        vo.setAvgHealth(-1);
        vo.setHealthLevel("no_data");

        DataScope scope = dataScopeService.currentScope();
        if (scope.isEmpty()) {
            return vo;
        }
        LambdaQueryWrapper<DbInstance> wrapper = new LambdaQueryWrapper<>();
        if (!scope.isUnrestricted()) {
            wrapper.in(DbInstance::getId, scope.instanceIds());
        }
        List<DbInstance> instances = mapper.selectList(wrapper);
        if (instances.isEmpty()) {
            return vo;
        }

        Map<Long, Integer> activeAlerts = countActiveAlerts(
                instances.stream().map(DbInstance::getId).toList());

        // 状态统计卡：normal + alert + abnormal + paused = total
        int normal = 0, alert = 0, abnormal = 0, paused = 0;
        for (DbInstance ins : instances) {
            int alerts = activeAlerts.getOrDefault(ins.getId(), 0);
            if ("paused".equals(ins.getStatus()))        paused++;
            else if ("abnormal".equals(ins.getStatus())) abnormal++;
            else if (alerts > 0)                          alert++;
            else                                          normal++;
        }
        vo.setTotal(instances.size());
        vo.setNormal(normal);
        vo.setAlert(alert);
        vo.setAbnormal(abnormal);
        vo.setPaused(paused);

        // 整体健康门面：聚合健康分 + 五维达标率（作业写回的快照均值）
        int healthSum = 0, scored = 0;
        Map<String, int[]> dimAcc = new LinkedHashMap<>(); // key -> [sum, count]
        for (DbInstance ins : instances) {
            if (ins.getHealth() != null && ins.getHealth() >= 0) {
                healthSum += ins.getHealth();
                scored++;
            }
            if (ins.getHealthDims() != null) {
                ins.getHealthDims().forEach((k, v) -> {
                    if (v != null && v >= 0) {
                        int[] acc = dimAcc.computeIfAbsent(k, x -> new int[2]);
                        acc[0] += v;
                        acc[1]++;
                    }
                });
            }
        }
        vo.setScoredCount(scored);
        int avgHealth = scored == 0 ? -1 : healthSum / scored;
        vo.setAvgHealth(avgHealth);
        vo.setHealthLevel(healthLevelOf(avgHealth));
        vo.setDims(DIM_LABELS.entrySet().stream().map(e -> {
            FleetOverviewVo.DimRate d = new FleetOverviewVo.DimRate();
            d.setKey(e.getKey());
            d.setLabel(e.getValue());
            int[] acc = dimAcc.get(e.getKey());
            d.setRate(acc == null || acc[1] == 0 ? -1 : acc[0] / acc[1]);
            return d;
        }).toList());

        // 近 7 天迷你趋势线
        vo.setTrends(buildTrends(instances, normal, alert, abnormal, paused));

        // 数据库类型分布 + 高风险 Top10
        Map<Long, DatabaseType> typeMap = loadTypeMap();
        Map<Long, String> versionMap = loadVersionMap();
        vo.setDbTypes(buildTypeDist(instances, typeMap, activeAlerts));
        vo.setTopRisk(buildTopRisk(instances, typeMap, versionMap, activeAlerts));
        return vo;
    }

    /**
     * 近 7 天状态趋势（含今天，最早在前）：
     * total 按实例建档时间回溯；alert 按当日触发过告警事件的去重实例数回溯（今天取当前活跃告警实例数）；
     * normal ≈ total - alert（今天取真实值）；abnormal/paused 无历史快照，输出当前值持平线。
     */
    private FleetOverviewVo.Trends buildTrends(List<DbInstance> instances,
                                               int normalNow, int alertNow, int abnormalNow, int pausedNow) {
        final int days = 7;
        OffsetDateTime todayStart = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);

        // 各天告警实例数：一次取回近 7 天事件，在内存中按天去重统计
        Set<Long> scopeIds = instances.stream().map(DbInstance::getId).collect(Collectors.toSet());
        List<AlertEvent> events = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .select(AlertEvent::getInstanceId, AlertEvent::getTriggerTime)
                .ge(AlertEvent::getTriggerTime, todayStart.minusDays(days - 1)));
        Map<Long, Set<Long>> alertInstancesByDayOffset = new HashMap<>();
        for (AlertEvent e : events) {
            if (e.getInstanceId() == null || e.getTriggerTime() == null) continue;
            if (!scopeIds.contains(e.getInstanceId())) continue;
            long offset = ChronoUnit.DAYS.between(todayStart, e.getTriggerTime().truncatedTo(ChronoUnit.DAYS));
            alertInstancesByDayOffset.computeIfAbsent(offset, k -> new java.util.HashSet<>()).add(e.getInstanceId());
        }

        List<Integer> total = new java.util.ArrayList<>(days);
        List<Integer> normal = new java.util.ArrayList<>(days);
        List<Integer> alert = new java.util.ArrayList<>(days);
        List<Integer> abnormal = new java.util.ArrayList<>(days);
        List<Integer> paused = new java.util.ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            OffsetDateTime dayEnd = todayStart.minusDays(i).plusDays(1);
            boolean isToday = i == 0;
            int totalAt = (int) instances.stream()
                    .filter(ins -> ins.getCreateTime() == null || ins.getCreateTime().isBefore(dayEnd))
                    .count();
            int alertAt = isToday ? alertNow
                    : alertInstancesByDayOffset.getOrDefault((long) -i, Set.of()).size();
            total.add(totalAt);
            alert.add(alertAt);
            normal.add(isToday ? normalNow : Math.max(0, totalAt - alertAt));
            abnormal.add(abnormalNow);
            paused.add(pausedNow);
        }
        FleetOverviewVo.Trends t = new FleetOverviewVo.Trends();
        t.setTotal(total);
        t.setNormal(normal);
        t.setAlert(alert);
        t.setAbnormal(abnormal);
        t.setPaused(paused);
        return t;
    }

    /** 各实例活跃（未恢复）告警事件数。 */
    private Map<Long, Integer> countActiveAlerts(List<Long> instanceIds) {
        if (instanceIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<AlertEvent> qw = new QueryWrapper<AlertEvent>()
                .select("instance_id", "COUNT(*) AS cnt")
                .in("status", ACTIVE_EVENT_STATUSES)
                .in("instance_id", instanceIds)
                .groupBy("instance_id");
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : alertEventMapper.selectMaps(qw)) {
            Object id = row.get("instance_id");
            Object cnt = row.get("cnt");
            if (id instanceof Number n && cnt instanceof Number c) {
                result.put(n.longValue(), c.intValue());
            }
        }
        return result;
    }

    private List<FleetOverviewVo.TypeDist> buildTypeDist(List<DbInstance> instances,
                                                         Map<Long, DatabaseType> typeMap,
                                                         Map<Long, Integer> activeAlerts) {
        Map<String, FleetOverviewVo.TypeDist> byType = new LinkedHashMap<>();
        for (DbInstance ins : instances) {
            String typeName = resolveTypeName(typeMap.get(ins.getDbTypeId()));
            if (typeName == null) {
                typeName = "未知类型";
            }
            FleetOverviewVo.TypeDist dist = byType.computeIfAbsent(typeName, k -> {
                FleetOverviewVo.TypeDist d = new FleetOverviewVo.TypeDist();
                d.setName(k);
                return d;
            });
            dist.setTotal(dist.getTotal() + 1);
            int alerts = activeAlerts.getOrDefault(ins.getId(), 0);
            if (alerts > 0) {
                dist.setAlert(dist.getAlert() + 1);
            } else if ("normal".equals(ins.getStatus())) {
                dist.setNormal(dist.getNormal() + 1);
            }
        }
        return List.copyOf(byType.values());
    }

    /** 高风险实例 Top10：健康分 <80 或存在活跃告警，按健康分从低到高（无分的排最前）。 */
    private List<FleetOverviewVo.RiskInstance> buildTopRisk(List<DbInstance> instances,
                                                            Map<Long, DatabaseType> typeMap,
                                                            Map<Long, String> versionMap,
                                                            Map<Long, Integer> activeAlerts) {
        List<DbInstance> risky = instances.stream()
                .filter(ins -> !"paused".equals(ins.getStatus()))
                .filter(ins -> (ins.getHealth() != null && ins.getHealth() < 80)
                        || activeAlerts.getOrDefault(ins.getId(), 0) > 0
                        || "abnormal".equals(ins.getStatus()))
                .sorted(Comparator.comparingInt(ins -> ins.getHealth() == null ? -1 : ins.getHealth()))
                .limit(10)
                .toList();
        if (risky.isEmpty()) {
            return List.of();
        }

        // 批量解析分组名与负责人姓名
        Set<Long> groupIds = risky.stream()
                .flatMap(i -> i.getGroupIds() == null ? java.util.stream.Stream.<Long>empty() : i.getGroupIds().stream())
                .collect(Collectors.toSet());
        Map<Long, String> groupNames = groupIds.isEmpty() ? Map.of()
                : instanceGroupMapper.selectByIds(groupIds).stream()
                        .collect(Collectors.toMap(InstanceGroup::getId, InstanceGroup::getName, (a, b) -> a));
        Set<Long> userIds = new java.util.HashSet<>();
        for (DbInstance ins : risky) {
            if (ins.getOwnerAId() != null) userIds.add(ins.getOwnerAId());
            if (ins.getOwnerBId() != null) userIds.add(ins.getOwnerBId());
        }
        Map<Long, String> userNames = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername(),
                                (a, b) -> a));

        return risky.stream().map(ins -> {
            FleetOverviewVo.RiskInstance r = new FleetOverviewVo.RiskInstance();
            r.setId(ins.getId());
            r.setName(ins.getName());
            r.setDbType(resolveTypeName(typeMap.get(ins.getDbTypeId())));
            r.setDbVersion(versionMap.get(ins.getDbVersionId()));
            r.setHost(ins.getHost());
            r.setPort(ins.getPort());
            r.setGroupNames(ins.getGroupIds() == null ? List.of()
                    : ins.getGroupIds().stream().map(groupNames::get).filter(java.util.Objects::nonNull).toList());
            r.setOwnerAName(ins.getOwnerAId() == null ? null : userNames.get(ins.getOwnerAId()));
            r.setOwnerBName(ins.getOwnerBId() == null ? null : userNames.get(ins.getOwnerBId()));
            r.setStatus(ins.getStatus());
            r.setHealth(ins.getHealth());
            r.setHealthLevel(ins.getHealth() == null ? "no_data" : healthLevelOf(ins.getHealth()));
            r.setActiveAlerts(activeAlerts.getOrDefault(ins.getId(), 0));
            return r;
        }).toList();
    }

    /** 健康分 → 等级（与 HealthScoreVo 口径一致；字典 health_level）。 */
    private static String healthLevelOf(int score) {
        if (score < 0)  return "no_data";
        if (score >= 90) return "excellent";
        if (score >= 75) return "good";
        if (score >= 60) return "warning";
        return "critical";
    }

    // ── 连接测试 ──────────────────────────────────────────────────────────────

    @Override
    public ConnectionTestVo testConnection(ConnectionTestRequest req) {
        String url = buildJdbcUrl(req);
        DriverManager.setLoginTimeout(5);
        try (Connection conn = DriverManager.getConnection(url,
                req.getConnUser(), req.getConnPassword())) {
            String version = conn.getMetaData().getDatabaseProductVersion();
            ConnectionTestVo vo = new ConnectionTestVo();
            vo.setVersion(StringUtils.hasText(version) ? version : "unknown");
            if ("mysql".equalsIgnoreCase(req.getDbType())) {
                vo.setChecks(checkMySqlPrivileges(conn, version, req.getConnUser()));
            } else if ("postgresql".equalsIgnoreCase(req.getDbType())) {
                vo.setChecks(checkPostgreSqlPrivileges(conn, req.getConnUser()));
            } else {
                vo.setChecks(List.of());
            }
            return vo;
        } catch (SQLException e) {
            throw new BusinessException("连接失败：" + rootMessage(e));
        }
    }

    /**
     * MySQL 采集账号权限逐项检测（差距分析 模块9）：
     * SHOW GRANTS 解析全局权限 + 关键系统表探测查询，缺失项标注受影响的监控能力与补齐 SQL。
     * 单项检测失败不影响其他项与连接测试主结论。
     */
    private List<ConnectionTestVo.PermissionCheck> checkMySqlPrivileges(Connection conn, String version, String connUser) {
        List<ConnectionTestVo.PermissionCheck> checks = new ArrayList<>();
        String user = "'" + (StringUtils.hasText(connUser) ? connUser : "monitor") + "'@'%'";
        Set<String> globalPrivs = new HashSet<>();
        boolean grantsReadable = true;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet rs = st.executeQuery("SHOW GRANTS")) {
                while (rs.next()) {
                    String grant = rs.getString(1);
                    if (grant == null) {
                        continue;
                    }
                    String upper = grant.toUpperCase();
                    // 仅解析全局授权行（ON *.*）中的权限名
                    if (upper.contains(" ON *.* ")) {
                        int idx = upper.indexOf("GRANT ");
                        int end = upper.indexOf(" ON *.* ");
                        if (idx >= 0 && end > idx) {
                            for (String p : upper.substring(idx + 6, end).split(",")) {
                                globalPrivs.add(p.trim());
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            grantsReadable = false;
            log.warn("SHOW GRANTS 失败，权限检测降级为探测查询: {}", e.getMessage());
        }
        boolean allPrivs = globalPrivs.contains("ALL PRIVILEGES");

        boolean isMySql56 = version != null && version.startsWith("5.6");

        // ① PROCESS：processlist / innodb_trx / 阻塞链
        Boolean hasProcess = grantsReadable ? (allPrivs || globalPrivs.contains("PROCESS")) : null;
        checks.add(ConnectionTestVo.PermissionCheck.of(
                "PROCESS 权限", hasProcess,
                "连接分析、活动会话、长事务、锁等待与阻塞链现场",
                "GRANT PROCESS ON *.* TO " + user + ";"));

        // ② REPLICATION CLIENT：复制状态 / binlog
        Boolean hasRepl = grantsReadable
                ? (allPrivs || globalPrivs.contains("REPLICATION CLIENT") || globalPrivs.contains("BINLOG MONITOR"))
                : null;
        checks.add(ConnectionTestVo.PermissionCheck.of(
                "REPLICATION CLIENT 权限", hasRepl,
                "复制状态监控、binlog 容量统计",
                "GRANT REPLICATION CLIENT ON *.* TO " + user + ";"));

        // ③ performance_schema 查询（5.7/8.0：Top SQL、慢SQL样本、语句摘要）
        if (!isMySql56) {
            checks.add(ConnectionTestVo.PermissionCheck.of(
                    "performance_schema 查询",
                    probeSelect(conn, "SELECT 1 FROM performance_schema.global_status LIMIT 1"),
                    "Top SQL 指纹分析、慢SQL样本、等待事件",
                    "GRANT SELECT ON performance_schema.* TO " + user + ";"));
            // ④ sys schema（阻塞链快照 5.7+ 使用 sys.innodb_lock_waits）
            checks.add(ConnectionTestVo.PermissionCheck.of(
                    "sys schema 访问",
                    probeSelect(conn, "SELECT 1 FROM sys.sys_config LIMIT 1"),
                    "锁等待明细、阻塞链现场快照",
                    "GRANT SELECT ON sys.* TO " + user + "; -- 并确保 PROCESS 权限"));
        } else {
            // 5.6：慢日志表路径
            checks.add(ConnectionTestVo.PermissionCheck.of(
                    "mysql.slow_log 查询",
                    probeSelect(conn, "SELECT 1 FROM mysql.slow_log LIMIT 1"),
                    "慢SQL样本采集（5.6 从慢日志表增量读取）",
                    "GRANT SELECT ON mysql.slow_log TO " + user
                            + "; -- 并开启 SET GLOBAL slow_query_log=ON; SET GLOBAL log_output='TABLE';"));
        }

        // ⑤ information_schema 基础查询（容量/表结构，一般默认可用）
        checks.add(ConnectionTestVo.PermissionCheck.of(
                "information_schema 查询",
                probeSelect(conn, "SELECT 1 FROM information_schema.tables LIMIT 1"),
                "容量统计、表信息巡检",
                "GRANT SELECT ON *.* TO " + user + ";"));
        return checks;
    }

    /**
     * PostgreSQL 采集账号权限逐项检测：
     * pg_monitor 角色成员探测 + 关键系统视图/函数探测查询，缺失项标注受影响的监控能力与补齐 SQL。
     */
    private List<ConnectionTestVo.PermissionCheck> checkPostgreSqlPrivileges(Connection conn, String connUser) {
        List<ConnectionTestVo.PermissionCheck> checks = new ArrayList<>();
        String user = StringUtils.hasText(connUser) ? connUser : "monitor";

        // ① pg_monitor 角色：pg_stat_activity 全量可见性（否则只能看到自己的会话，锁/事务监控失真）
        Boolean hasMonitorRole = null;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet rs = st.executeQuery(
                    "SELECT pg_has_role(current_user, 'pg_monitor', 'member') AS m")) {
                if (rs.next()) {
                    hasMonitorRole = rs.getBoolean("m");
                }
            }
        } catch (SQLException e) {
            log.warn("pg_monitor 角色探测失败: {}", e.getMessage());
        }
        checks.add(ConnectionTestVo.PermissionCheck.of(
                "pg_monitor 角色", hasMonitorRole,
                "会话/锁/事务全量可见性（缺失时只能看到采集账号自身的会话）",
                "GRANT pg_monitor TO " + user + ";"));

        // ② pg_stat_activity 查询（连接/事务/锁等待监控的数据来源）
        checks.add(ConnectionTestVo.PermissionCheck.of(
                "pg_stat_activity 查询",
                probeSelect(conn, "SELECT 1 FROM pg_stat_activity LIMIT 1"),
                "连接分析、活跃会话、长事务、锁等待",
                "GRANT pg_monitor TO " + user + ";"));

        // ③ pg_stat_replication 查询（主库侧复制监控）
        checks.add(ConnectionTestVo.PermissionCheck.of(
                "pg_stat_replication 查询",
                probeSelect(conn, "SELECT 1 FROM pg_stat_replication LIMIT 1"),
                "复制状态与从库延迟监控",
                "GRANT pg_monitor TO " + user + ";"));

        // ④ 容量统计（pg_database_size 需要对目标库的 CONNECT 权限）
        checks.add(ConnectionTestVo.PermissionCheck.of(
                "容量统计（pg_database_size）",
                probeSelect(conn, "SELECT pg_database_size(current_database())"),
                "库容量趋势统计",
                "GRANT CONNECT ON DATABASE <db> TO " + user + ";"));

        // ⑤ pg_stat_statements 扩展（二期 Top SQL 依赖，缺失不影响一期能力）
        checks.add(ConnectionTestVo.PermissionCheck.of(
                "pg_stat_statements 扩展（可选）",
                probeSelect(conn, "SELECT 1 FROM pg_stat_statements LIMIT 1"),
                "Top SQL 指纹分析（二期）；需在 shared_preload_libraries 中加载并 CREATE EXTENSION",
                "CREATE EXTENSION IF NOT EXISTS pg_stat_statements; -- 并授予 pg_read_all_stats"));
        return checks;
    }

    /** 探测查询：能查到/查空都算有权限，报错视为无权限（超时视为无法确认）。 */
    private Boolean probeSelect(Connection conn, String sql) {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet ignored = st.executeQuery(sql)) {
                return true;
            }
        } catch (SQLException e) {
            String state = e.getSQLState();
            // 42000/HY000 权限类错误 → 明确无权限；其他（如超时）无法确认
            if (state != null && (state.startsWith("42") || "HY000".equals(state))) {
                return false;
            }
            return e.getMessage() != null && e.getMessage().toLowerCase().contains("denied") ? false : null;
        }
    }

    private String buildJdbcUrl(ConnectionTestRequest req) {
        String host = req.getHost();
        int port = req.getPort();
        String type = req.getDbType() == null ? "" : req.getDbType().toLowerCase();
        return switch (type) {
            case "mysql" -> "jdbc:mysql://" + host + ":" + port
                    + "/?connectTimeout=5000&socketTimeout=8000&useSSL=false"
                    + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
            case "postgresql" -> "jdbc:postgresql://" + host + ":" + port
                    + "/" + (StringUtils.hasText(req.getDatabaseName()) ? req.getDatabaseName() : "postgres")
                    + "?connectTimeout=5&socketTimeout=8";
            case "oracle" -> "jdbc:oracle:thin:@" + host + ":" + port + ":orcl";
            case "sqlserver" -> "jdbc:sqlserver://" + host + ":" + port
                    + ";encrypt=false;loginTimeout=5";
            default -> throw new BusinessException("暂不支持的数据库类型：" + req.getDbType());
        };
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    // ── 采集分片 ──────────────────────────────────────────────────────────────

    @Override
    public List<CollectTargetVo> listByShard(int shardIndex, int shardTotal) {
        // P1-4: 分片过滤下推到 SQL（ABS(HASHTEXT(instance_code)) % shardTotal = shardIndex）
        List<DbInstance> shard = mapper.selectByShard(shardIndex, shardTotal);
        Map<Long, DatabaseType> typeMap = loadTypeMap();
        Map<Long, String> versionMap = loadVersionMap();
        return shard.stream()
                .map(e -> {
                    DatabaseType dt = typeMap.get(e.getDbTypeId());
                    CollectTargetVo t = InstanceConverter.toCollectTarget(
                            e,
                            resolveTypeName(dt),
                            versionMap.get(e.getDbVersionId()),
                            dt != null ? dt.getDriverClass() : null,
                            dt != null ? dt.getUrlTemplate() : null);
                    // 采集侧需明文连接凭据：读取时解密（历史明文原样返回）
                    t.setConnPassword(passwordCipher.decrypt(t.getConnPassword()));
                    return t;
                })
                .toList();
    }

    @Override
    public CollectTargetVo getCollectTarget(Long instanceId) {
        DbInstance ins = mapper.selectById(instanceId);
        if (ins == null) {
            return null;
        }
        DatabaseType dt = ins.getDbTypeId() == null ? null : databaseTypeMapper.selectById(ins.getDbTypeId());
        String versionCode = null;
        if (ins.getDbVersionId() != null) {
            var dv = databaseVersionMapper.selectById(ins.getDbVersionId());
            versionCode = dv == null ? null : dv.getVersionCode();
        }
        CollectTargetVo t = InstanceConverter.toCollectTarget(
                ins,
                resolveTypeName(dt),
                versionCode,
                dt != null ? dt.getDriverClass() : null,
                dt != null ? dt.getUrlTemplate() : null);
        t.setConnPassword(passwordCipher.decrypt(t.getConnPassword()));
        return t;
    }
}
