package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.InstanceCapabilityVo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL Server 版本、Edition、权限和可选组件的只读实时探测。 */
@Component
public class SqlServerCapabilityProbe {
    private static final String AVAILABLE = "available";
    private static final String LIMITED = "limited";
    private static final String PERMISSION_DENIED = "permission_denied";
    private static final String VERSION_NOT_SUPPORT = "version_not_support";
    private static final String EDITION_NOT_SUPPORT = "edition_not_support";
    private static final String NOT_ENABLED = "not_enabled";
    private static final String COLLECT_ERROR = "collect_error";

    public List<InstanceCapabilityVo> probe(CollectTargetVo target, String configuredVersion) {
        List<InstanceCapabilityVo> result = new ArrayList<>();
        try (Connection conn = open(target);
             Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            int major = 0;
            try (ResultSet rs = st.executeQuery("""
                    SELECT CAST(SERVERPROPERTY('ProductMajorVersion') AS int) AS major_version,
                           CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(128)) AS product_version,
                           CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) AS edition,
                           CAST(SERVERPROPERTY('EngineEdition') AS int) AS engine_edition,
                           CAST(SERVERPROPERTY('IsHadrEnabled') AS int) AS hadr_enabled
                    """)) {
                if (rs.next()) {
                    major = rs.getInt("major_version");
                    boolean majorMissing = rs.wasNull();
                    String version = rs.getString("product_version");
                    if (majorMissing || major == 0) major = majorFromProductVersion(version);
                    result.add(versionCapability(major, version, configuredVersion));
                    result.add(InstanceCapabilityVo.of("edition", "Edition 能力（" + rs.getString("edition") + "）",
                            AVAILABLE, "EngineEdition=" + rs.getInt("engine_edition")));
                    result.add(agentCapability(rs.getString("edition"), rs.getInt("engine_edition")));
                    result.add(alwaysOnCapability(major, rs.getInt("hadr_enabled")));
                }
            }
            result.add(probe(conn, "database_catalog", "数据库状态目录",
                    "SELECT TOP (1) database_id FROM sys.databases WHERE database_id > 4",
                    "无法读取用户数据库状态，数据库级可用性结论受限"));
            result.add(probe(conn, "suspect_pages", "疑似损坏页线索",
                    "SELECT TOP (1) database_id FROM msdb.dbo.suspect_pages",
                    "无法读取 msdb suspect_pages，页面损坏线索不可用"));            result.add(probe(conn, "server_performance_state", "服务器性能 DMV",
                    "SELECT TOP (1) scheduler_id FROM sys.dm_os_schedulers",
                    "缺少服务器性能状态权限，CPU、等待、内存、I/O 与 HA 诊断受限"));
            String databaseStateSql = major > 0 && major < 11
                    ? "SELECT TOP (1) file_id FROM sys.dm_db_file_space_usage"
                    : "SELECT TOP (1) database_id FROM sys.dm_db_log_space_usage";
            result.add(probe(conn, "database_performance_state", "数据库性能 DMV",
                    databaseStateSql, "缺少数据库性能状态权限，日志与数据库空间诊断受限"));
            result.add(probe(conn, "backup_history", "备份历史",
                    "SELECT TOP (1) backup_set_id FROM msdb.dbo.backupset",
                    "无法读取 msdb 备份历史，备份覆盖与恢复准备度受限"));
            result.add(securityMetadata(conn));
            result.add(queryStore(conn, major));
        } catch (Exception e) {
            result.add(InstanceCapabilityVo.of("live_probe", "实时能力探测",
                    permissionDenied(e) ? PERMISSION_DENIED : COLLECT_ERROR,
                    friendly(e)));
        }
        return result;
    }

    InstanceCapabilityVo versionCapability(int major, String actual, String configured) {
        String label = "版本支持状态（" + (actual == null ? configured : actual) + "）";
        return switch (major) {
            case 10 -> InstanceCapabilityVo.of("version_support", label, AVAILABLE,
                    "已启用兼容监控；该版本不提供 Query Store 和 Always On，Top SQL 自动降级为 DMV 累计快照");
            case 11, 12 -> InstanceCapabilityVo.of("version_support", label, AVAILABLE,
                    "已启用兼容监控；该版本不提供 Query Store，Top SQL 自动降级为 DMV 累计快照");
            case 13, 14, 15, 16, 17 -> InstanceCapabilityVo.of("version_support", label, AVAILABLE, null);
            default -> InstanceCapabilityVo.of("version_support", label, VERSION_NOT_SUPPORT,
                    "平台支持 SQL Server 2008 R2、2012、2014、2016、2017、2019、2022、2025");
        };
    }

    InstanceCapabilityVo alwaysOnCapability(int major, int enabled) {
        if (major > 0 && major < 11) {
            return InstanceCapabilityVo.of("always_on", "Always On 可用组", VERSION_NOT_SUPPORT,
                    "SQL Server 2008 R2 不提供 Always On 可用组，不评估相关告警和场景");
        }
        return InstanceCapabilityVo.of("always_on", "Always On 可用组",
                enabled == 1 ? AVAILABLE : NOT_ENABLED,
                enabled == 1 ? null : "当前实例未启用 Always On；不会展示可用组告警");
    }

    InstanceCapabilityVo agentCapability(String edition, int engineEdition) {
        if (engineEdition == 4 || (edition != null && edition.toLowerCase().contains("express"))) {
            return InstanceCapabilityVo.of("sql_agent", "SQL Server Agent", EDITION_NOT_SUPPORT,
                    "Express Edition 不提供 SQL Server Agent，不评估作业失败和运行状态");
        }
        return InstanceCapabilityVo.of("sql_agent", "SQL Server Agent", AVAILABLE, null);
    }
    static int majorFromProductVersion(String version) {
        if (version == null || version.isBlank()) return 0;
        try {
            int separator = version.indexOf('.');
            return Integer.parseInt(separator < 0 ? version : version.substring(0, separator));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private InstanceCapabilityVo probe(Connection conn, String code, String name,
                                       String sql, String deniedMessage) {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet ignored = st.executeQuery(sql)) {
                return InstanceCapabilityVo.of(code, name, AVAILABLE, null);
            }
        } catch (Exception e) {
            return InstanceCapabilityVo.of(code, name,
                    permissionDenied(e) ? PERMISSION_DENIED : LIMITED,
                    permissionDenied(e) ? deniedMessage : friendly(e));
        }
    }

    private InstanceCapabilityVo securityMetadata(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet rs = st.executeQuery(
                    "SELECT CAST(HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DEFINITION') AS int)")) {
                return securityMetadataCapability(rs.next() && rs.getInt(1) == 1);
            }
        } catch (Exception e) {
            return InstanceCapabilityVo.of("security_health", "安全配置健康评分",
                    permissionDenied(e) ? PERMISSION_DENIED : LIMITED,
                    permissionDenied(e) ? "无法检查安全元数据权限，安全配置维度暂不评分" : friendly(e));
        }
    }

    InstanceCapabilityVo securityMetadataCapability(boolean available) {
        return InstanceCapabilityVo.of("security_health", "安全配置健康评分",
                available ? AVAILABLE : PERMISSION_DENIED,
                available ? null : "监控账号缺少 VIEW ANY DEFINITION，无法完整检查登录账号，安全配置维度暂不评分");
    }
    private InstanceCapabilityVo queryStore(Connection conn, int major) {
        if (major > 0 && major < 13) {
            return InstanceCapabilityVo.of("query_store", "Query Store", VERSION_NOT_SUPPORT,
                    "SQL Server 2008 R2/2012/2014 不提供 Query Store，Top SQL 已自动降级为 DMV 累计快照");
        }
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet rs = st.executeQuery(
                    "SELECT actual_state_desc, readonly_reason FROM sys.database_query_store_options")) {
                if (!rs.next()) {
                    return InstanceCapabilityVo.of("query_store", "Query Store", NOT_ENABLED, "当前数据库未启用 Query Store");
                }
                String state = rs.getString(1);
                String status = "READ_WRITE".equalsIgnoreCase(state) ? AVAILABLE
                        : "OFF".equalsIgnoreCase(state) ? NOT_ENABLED : LIMITED;
                return InstanceCapabilityVo.of("query_store", "Query Store", status,
                        "READ_WRITE".equalsIgnoreCase(state) ? null
                                : "当前数据库 Query Store 状态为 " + state + "，历史 SQL 与计划诊断受限");
            }
        } catch (Exception e) {
            return InstanceCapabilityVo.of("query_store", "Query Store",
                    permissionDenied(e) ? PERMISSION_DENIED : LIMITED, friendly(e));
        }
    }

    private static Connection open(CollectTargetVo target) throws Exception {
        DriverManager.setLoginTimeout(5);
        String url = target.getUrlTemplate()
                .replace("{host}", target.getHost())
                .replace("{port}", String.valueOf(target.getPort()))
                .replace("{database}", StringUtils.hasText(target.getDatabaseName())
                        ? target.getDatabaseName() : "master");
        return DriverManager.getConnection(url, target.getConnUser(), target.getConnPassword());
    }

    private static boolean permissionDenied(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return message.contains("permission") || message.contains("denied")
                || message.contains("not have permission");
    }

    private static String friendly(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (permissionDenied(e)) return "采集账号权限不足，请按实例版本授予最小性能状态读取权限";
        return "实时探测失败：" + (message.length() > 200 ? message.substring(0, 200) : message);
    }
}
