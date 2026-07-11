package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.api.response.InstanceCapabilityVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.CollectLog;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.CollectLogMapper;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 实例运行时能力状态检测实现。
 *
 * <p>判断维度（§20.1.1，按当前规模取够用的子集）：
 * <ul>
 *   <li>版本能力：dbVersion（5.6 无 P_S 语句摘要 / sys schema，8.0.22+ 才有 error_log 表）；</li>
 *   <li>关联关系：hostId 未关联 → 主机资源监控不适用；</li>
 *   <li>采集健康：collect_log 最新一条失败 → 采集异常；从未产生日志 → 数据不足。</li>
 * </ul>
 * 状态值由字典 capability_status 统一维护，不在此处散落展示文案颜色。
 */
@Service
public class InstanceCapabilityServiceImpl implements InstanceCapabilityService {

    // 状态编码（字典 capability_status）
    private static final String AVAILABLE = "available";
    private static final String LIMITED = "limited";
    private static final String VERSION_NOT_SUPPORT = "version_not_support";
    private static final String NOT_APPLICABLE = "not_applicable";
    private static final String COLLECT_ERROR = "collect_error";
    private static final String NO_DATA = "no_data";

    /** 分钟级采集"新鲜"窗口：最近一条日志早于该窗口视为采集停滞。 */
    private static final int MINUTE_STALE_MINUTES = 10;

    /** 审计插件启用状态指标（AuditPluginItem，天级）。 */
    private static final String AUDIT_PLUGIN_ACTIVE_METRIC = "mysql.security.audit_plugin_active";

    private final DbInstanceMapper instanceMapper;
    private final DatabaseTypeMapper databaseTypeMapper;
    private final DatabaseVersionMapper versionMapper;
    private final CollectLogMapper collectLogMapper;
    private final TsMetricLatestDao metricLatestDao;

    public InstanceCapabilityServiceImpl(DbInstanceMapper instanceMapper,
                                         DatabaseTypeMapper databaseTypeMapper,
                                         DatabaseVersionMapper versionMapper,
                                         CollectLogMapper collectLogMapper,
                                         TsMetricLatestDao metricLatestDao) {
        this.instanceMapper = instanceMapper;
        this.databaseTypeMapper = databaseTypeMapper;
        this.versionMapper = versionMapper;
        this.collectLogMapper = collectLogMapper;
        this.metricLatestDao = metricLatestDao;
    }

    @Override
    public List<InstanceCapabilityVo> detect(Long instanceId) {
        DbInstance ins = instanceMapper.selectById(instanceId);
        if (ins == null) {
            throw new BusinessException("实例不存在");
        }
        String dbTypeCode = resolveDbTypeCode(ins);
        if ("POSTGRESQL".equals(dbTypeCode)) {
            return detectPostgreSql(ins);
        }
        if (!"MYSQL".equals(dbTypeCode)) {
            return List.of(InstanceCapabilityVo.of("database", "数据库类型", VERSION_NOT_SUPPORT,
                    "暂不支持数据库类型：" + (dbTypeCode == null ? "未配置" : dbTypeCode)));
        }
        String version = resolveVersion(ins);
        boolean is56 = version != null && version.startsWith("5.6");
        boolean is80Plus = version != null && version.startsWith("8.");

        CollectLog latest1m = latestLog(instanceId, "1m");
        List<InstanceCapabilityVo> list = new ArrayList<>();

        // ① 采集状态（总开关性质：异常时其余能力的数据可信度都会受影响）
        list.add(collectCapability(ins, latest1m));

        // ② Top SQL 指纹分析
        if (is56) {
            list.add(InstanceCapabilityVo.of("top_sql", "Top SQL 指纹分析", VERSION_NOT_SUPPORT,
                    "MySQL 5.6 不支持 performance_schema 语句摘要，已降级为慢日志样本采集"));
        } else {
            list.add(InstanceCapabilityVo.of("top_sql", "Top SQL 指纹分析", AVAILABLE, null));
        }

        // ③ 慢SQL样本
        if (is56) {
            list.add(InstanceCapabilityVo.of("slow_sql_sample", "慢SQL样本", LIMITED,
                    "5.6 从慢日志表增量采集：需在目标库开启 slow_query_log=ON 且 log_output 含 TABLE"));
        } else {
            list.add(InstanceCapabilityVo.of("slow_sql_sample", "慢SQL样本", AVAILABLE, null));
        }

        // ④ 锁等待与阻塞链
        if (is56) {
            list.add(InstanceCapabilityVo.of("lock_analysis", "锁等待与阻塞链", LIMITED,
                    "5.6 无 sys schema，锁等待明细与阻塞链现场为基础版（information_schema 还原）"));
        } else {
            list.add(InstanceCapabilityVo.of("lock_analysis", "锁等待与阻塞链", AVAILABLE, null));
        }

        // ⑤ 等待事件分析（performance_schema waits summary，5.7/8.0）
        if (is56) {
            list.add(InstanceCapabilityVo.of("wait_events", "等待事件分析", VERSION_NOT_SUPPORT,
                    "MySQL 5.6 无稳定的等待事件汇总能力，已降级为锁统计与 InnoDB 引擎状态解析"));
        } else {
            list.add(InstanceCapabilityVo.of("wait_events", "等待事件分析", AVAILABLE, null));
        }

        // ⑤.5 表 I/O 对象级分析（performance_schema 表级 I/O 汇总，5.7/8.0）
        if (is56) {
            list.add(InstanceCapabilityVo.of("table_io", "表 I/O 热点与索引使用分析", VERSION_NOT_SUPPORT,
                    "MySQL 5.6 不采集表级 I/O 汇总，表 I/O 热点与未使用索引扫描不可用"));
        } else {
            list.add(InstanceCapabilityVo.of("table_io", "表 I/O 热点与索引使用分析", AVAILABLE, null));
        }

        // ⑥ 安全审计（performance_schema 语句摘要，5.7/8.0）
        if (is56) {
            list.add(InstanceCapabilityVo.of("security_audit", "权限变更与危险操作审计", VERSION_NOT_SUPPORT,
                    "MySQL 5.6 无语句摘要能力，权限变更/危险操作审计不可用；认证失败与来源白名单检测仍可用"));
        } else {
            list.add(InstanceCapabilityVo.of("security_audit", "权限变更与危险操作审计", AVAILABLE, null));
        }

        // ⑥.5 完整审计（数据库侧审计插件对接：audit_log / server_audit）
        Double auditActive = metricLatestDao.latestFrom1d(instanceId,
                List.of(AUDIT_PLUGIN_ACTIVE_METRIC)).get(AUDIT_PLUGIN_ACTIVE_METRIC);
        if (auditActive != null && auditActive >= 1) {
            list.add(InstanceCapabilityVo.of("full_audit", "完整审计（审计插件）", AVAILABLE, null));
        } else {
            list.add(InstanceCapabilityVo.of("full_audit", "完整审计（审计插件）", LIMITED,
                    "未检测到审计插件（MySQL Enterprise Audit / Percona audit_log / MariaDB server_audit）。"
                            + "当前使用平台轻审计（可发现权限变更与危险操作，但无法还原执行账号与来源）；"
                            + "如需合规级完整审计，请在目标库安装审计插件后自动识别"));
        }

        // ⑦ 错误日志监控（performance_schema.error_log 仅 8.0.22+）
        if (is80Plus) {
            list.add(InstanceCapabilityVo.of("error_log", "错误日志监控", AVAILABLE, null));
        } else {
            list.add(InstanceCapabilityVo.of("error_log", "错误日志监控", VERSION_NOT_SUPPORT,
                    "需 MySQL 8.0.22+（performance_schema.error_log 表），当前版本无法结构化读取错误日志"));
        }

        // ⑧ 主机资源监控
        if (ins.getHostId() == null) {
            list.add(InstanceCapabilityVo.of("host_metrics", "主机资源监控", NOT_APPLICABLE,
                    "未关联主机：请在「实例管理」编辑实例并选择所在主机后，即可查看 CPU / 内存 / 磁盘指标"));
        } else {
            list.add(InstanceCapabilityVo.of("host_metrics", "主机资源监控", AVAILABLE, null));
        }
        return list;
    }

    /** 扩展探测指标（PgExtensionsItem，天级）：0=未启用 1=差一步 2=就绪。 */
    private static final String PG_STAT_STATEMENTS_METRIC = "pg.ext.pg_stat_statements";

    /**
     * PostgreSQL 能力清单：基础监控（连接/吞吐/缓存/锁/事务/复制/容量）可用；
     * Top SQL 依赖 pg_stat_statements 扩展（按天级探测指标动态判定并给安装引导）；
     * 慢 SQL 样本与等待事件基于 pg_stat_activity 采样，无扩展依赖。
     */
    private List<InstanceCapabilityVo> detectPostgreSql(DbInstance ins) {
        CollectLog latest1m = latestLog(ins.getId(), "1m");
        List<InstanceCapabilityVo> list = new ArrayList<>();
        list.add(collectCapability(ins, latest1m));
        list.add(InstanceCapabilityVo.of("connections", "连接与事务监控", AVAILABLE, null));
        list.add(InstanceCapabilityVo.of("lock_analysis", "锁等待监控", AVAILABLE, null));
        list.add(InstanceCapabilityVo.of("replication", "流复制监控", AVAILABLE, null));
        list.add(InstanceCapabilityVo.of("capacity", "容量趋势", AVAILABLE, null));

        // Top SQL：按 pg_stat_statements 扩展状态动态判定（差异化安装引导）
        Double extStatus = metricLatestDao.latestFrom1d(ins.getId(),
                List.of(PG_STAT_STATEMENTS_METRIC)).get(PG_STAT_STATEMENTS_METRIC);
        if (extStatus != null && extStatus >= 2) {
            list.add(InstanceCapabilityVo.of("top_sql", "Top SQL 指纹分析", AVAILABLE, null));
        } else if (extStatus != null && extStatus >= 1) {
            list.add(InstanceCapabilityVo.of("top_sql", "Top SQL 指纹分析", LIMITED,
                    "pg_stat_statements 扩展只差一步：若已在 shared_preload_libraries 加载，"
                            + "在目标库执行 CREATE EXTENSION pg_stat_statements 即可；"
                            + "若只执行过 CREATE EXTENSION，还需将其加入 shared_preload_libraries 并重启数据库"));
        } else {
            list.add(InstanceCapabilityVo.of("top_sql", "Top SQL 指纹分析", LIMITED,
                    "未检测到 pg_stat_statements 扩展：请在目标库 postgresql.conf 的 "
                            + "shared_preload_libraries 中加入 pg_stat_statements 并重启，"
                            + "然后执行 CREATE EXTENSION pg_stat_statements；启用后自动开始采集 Top SQL"));
        }
        list.add(InstanceCapabilityVo.of("slow_sql_sample", "慢SQL样本", AVAILABLE,
                "基于 pg_stat_activity 运行中采样：抓取执行超阈值（log_min_duration_statement，"
                        + "未开启时默认 5 秒）的活跃语句，为抽样非全量"));
        list.add(InstanceCapabilityVo.of("wait_events", "等待事件分析", AVAILABLE, null));
        if (ins.getHostId() == null) {
            list.add(InstanceCapabilityVo.of("host_metrics", "主机资源监控", NOT_APPLICABLE,
                    "未关联主机：请在「实例管理」编辑实例并选择所在主机后，即可查看 CPU / 内存 / 磁盘指标"));
        } else {
            list.add(InstanceCapabilityVo.of("host_metrics", "主机资源监控", AVAILABLE, null));
        }
        return list;
    }

    private String resolveDbTypeCode(DbInstance ins) {
        if (ins.getDbTypeId() == null) {
            return null;
        }
        DatabaseType type = databaseTypeMapper.selectById(ins.getDbTypeId());
        return type == null || type.getCode() == null ? null : type.getCode().trim().toUpperCase();
    }

    /** 采集状态能力：暂停 → 不适用；最新失败 → 采集异常；无日志/停滞 → 数据不足。 */
    private InstanceCapabilityVo collectCapability(DbInstance ins, CollectLog latest1m) {
        if ("paused".equals(ins.getStatus())) {
            return InstanceCapabilityVo.of("collect", "指标采集", NOT_APPLICABLE,
                    "实例已暂停采集：在「实例管理」恢复采集后数据将继续更新");
        }
        if (latest1m == null) {
            return InstanceCapabilityVo.of("collect", "指标采集", NO_DATA,
                    "尚未产生采集数据：新接入实例请等待 1-2 个采集周期，或检查采集任务调度是否运行");
        }
        if (Boolean.FALSE.equals(latest1m.getSuccess())) {
            String reason = StringUtils.hasText(latest1m.getErrorMessage())
                    ? latest1m.getErrorMessage() : "未知原因";
            return InstanceCapabilityVo.of("collect", "指标采集", COLLECT_ERROR,
                    "最近一次采集失败：" + truncate(reason, 200) + "。各页面展示的可能是较早的数据");
        }
        OffsetDateTime staleBefore = OffsetDateTime.now().minusMinutes(MINUTE_STALE_MINUTES);
        if (latest1m.getCollectTime() != null && latest1m.getCollectTime().isBefore(staleBefore)) {
            return InstanceCapabilityVo.of("collect", "指标采集", NO_DATA,
                    "采集数据已停滞超过 " + MINUTE_STALE_MINUTES + " 分钟（最近成功："
                            + latest1m.getCollectTime().toLocalDateTime().toLocalTime() + "），请检查采集任务与目标库连通性");
        }
        return InstanceCapabilityVo.of("collect", "指标采集", AVAILABLE, null);
    }

    private String resolveVersion(DbInstance ins) {
        if (ins.getDbVersionId() == null) {
            return null;
        }
        DatabaseVersion v = versionMapper.selectById(ins.getDbVersionId());
        return v == null ? null : v.getVersionCode();
    }

    private CollectLog latestLog(Long instanceId, String frequency) {
        List<CollectLog> logs = collectLogMapper.selectRecent(instanceId, frequency, 1);
        return logs.isEmpty() ? null : logs.get(0);
    }

    private static String truncate(String value, int maxLen) {
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
