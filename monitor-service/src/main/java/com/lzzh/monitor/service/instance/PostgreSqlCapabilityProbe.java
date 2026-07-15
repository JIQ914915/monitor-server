package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.InstanceCapabilityVo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PostgreSqlCapabilityProbe {
    private static final String AVAILABLE = "available";
    private static final String LIMITED = "limited";
    private static final String PERMISSION_DENIED = "permission_denied";
    private static final String VERSION_NOT_SUPPORT = "version_not_support";
    private static final String COLLECT_ERROR = "collect_error";

    /** PostgreSQL 官方 2026-05-14 安全更新后的当前小版本。 */
    private static final Map<Integer, Integer> CURRENT_MINORS =
            Map.of(14, 23, 15, 18, 16, 14, 17, 10, 18, 4);
    private static final Map<Integer, String> EOL_DATES =
            Map.of(14, "2026-11-12", 15, "2027-11-11", 16, "2028-11-09",
                    17, "2029-11-08", 18, "2030-11-14");

    public List<InstanceCapabilityVo> probe(CollectTargetVo target, String configuredVersion) {
        List<InstanceCapabilityVo> result = new ArrayList<>();
        try (Connection conn = open(target)) {
            String actualVersion = settingValue(conn, "server_version");
            int major = major(actualVersion);
            result.add(versionCapability(actualVersion, major));
            result.add(view(conn, "pg_stat_io", "PG 统一 I/O 统计", major >= 16,
                    "PostgreSQL 16+ 才支持 pg_stat_io"));
            result.add(view(conn, "pg_stat_checkpointer", "检查点独立统计", major >= 17,
                    "PostgreSQL 17+ 才支持 pg_stat_checkpointer"));
            result.add(InstanceCapabilityVo.of("pg_stat_io_bytes", "字节级 I/O 统计",
                    major >= 18 ? AVAILABLE : VERSION_NOT_SUPPORT,
                    major >= 18 ? null : "PostgreSQL 18+ 才支持 pg_stat_io 字节指标"));
            result.add(view(conn, "pg_stat_progress_vacuum", "VACUUM 进度", true, null));
            result.add(view(conn, "pg_stat_progress_analyze", "ANALYZE 进度", true, null));
            result.add(view(conn, "pg_stat_progress_create_index", "索引创建进度", true, null));
            result.add(view(conn, "pg_stat_progress_copy", "COPY 进度", true, null));
            result.add(extension(conn, "pg_stat_statements", "Top SQL 指纹分析", true));
            result.add(extension(conn, "pgaudit", "pgaudit 审计", false));
            result.add(extension(conn, "pgstattuple", "精确膨胀检查", false));
            result.add(extension(conn, "amcheck", "索引一致性检查", false));
            result.add(library(conn, "auto_explain", "auto_explain 慢 SQL 计划"));
            result.add(setting(conn, "track_io_timing", "I/O 耗时统计"));
            result.add(setting(conn, "track_wal_io_timing", "WAL I/O 耗时统计"));
            result.add(setting(conn, "compute_query_id", "Query ID 计算"));
            result.add(monitorVisibility(conn));
        } catch (Exception e) {
            if (result.stream().noneMatch(item -> "version_support".equals(item.getCapability()))) {
                result.add(versionCapability(configuredVersion, major(configuredVersion)));
            }
            result.add(InstanceCapabilityVo.of("live_probe", "实时能力探测",
                    isPermissionDenied(e) ? PERMISSION_DENIED : COLLECT_ERROR, friendly(e)));
        }
        return result;
    }

    private InstanceCapabilityVo versionCapability(String version, int major) {
        String normalized = normalizeVersion(version);
        String name = "版本支持状态（" + normalized + "）";
        if (major >= 14 && major <= 18) {
            Integer currentMinor = CURRENT_MINORS.get(major);
            int actualMinor = minor(normalized);
            if (currentMinor != null && actualMinor >= 0 && actualMinor < currentMinor) {
                return InstanceCapabilityVo.of("version_support", name, LIMITED,
                        "当前为 " + normalized + "，官方安全小版本为 " + major + "." + currentMinor
                                + "，建议尽快完成小版本升级；该主版本 EOL：" + EOL_DATES.get(major));
            }
            if (major == 14) {
                return InstanceCapabilityVo.of("version_support", name, LIMITED,
                        "PostgreSQL 14 将于 " + EOL_DATES.get(major) + " 结束官方支持，请规划主版本升级");
            }
            return InstanceCapabilityVo.of("version_support", name, AVAILABLE,
                    "官方当前安全小版本 " + major + "." + currentMinor + "；EOL：" + EOL_DATES.get(major));
        }
        if (major == 13) {
            return InstanceCapabilityVo.of("version_support", name, VERSION_NOT_SUPPORT,
                    "PostgreSQL 13 已于 2025-11-13 结束官方支持，平台仅保留兼容基线，请尽快升级");
        }
        return InstanceCapabilityVo.of("version_support", name, VERSION_NOT_SUPPORT,
                "平台正式支持 PostgreSQL 14～18，当前版本：" + normalized);
    }

    private InstanceCapabilityVo view(Connection conn, String name, String label,
                                      boolean versionSupported, String unsupportedMessage) throws Exception {
        if (!versionSupported) {
            return InstanceCapabilityVo.of(name, label, VERSION_NOT_SUPPORT, unsupportedMessage);
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT to_regclass('pg_catalog.' || ?::text) IS NOT NULL")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                boolean exists = rs.next() && rs.getBoolean(1);
                return InstanceCapabilityVo.of(name, label, exists ? AVAILABLE : VERSION_NOT_SUPPORT,
                        exists ? null : "当前实例未提供系统视图 " + name);
            }
        }
    }

    private InstanceCapabilityVo extension(Connection conn, String name, String label,
                                           boolean preloadRequired) throws Exception {
        String installed = null;
        String available = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT default_version, installed_version FROM pg_available_extensions WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    available = rs.getString(1);
                    installed = rs.getString(2);
                }
            }
        }
        if (available == null) {
            return InstanceCapabilityVo.of("extension." + name, label, VERSION_NOT_SUPPORT,
                    "当前 PostgreSQL 安装未提供扩展 " + name);
        }
        boolean preloaded = !preloadRequired || settingValue(conn, "shared_preload_libraries")
                .toLowerCase().contains(name.toLowerCase());
        if (installed != null && preloaded) {
            return InstanceCapabilityVo.of("extension." + name, label, AVAILABLE, null);
        }
        String message = installed == null ? "扩展可用但当前数据库尚未执行 CREATE EXTENSION " + name
                : "扩展已创建但未加入 shared_preload_libraries，配置后需重启实例";
        return InstanceCapabilityVo.of("extension." + name, label, LIMITED, message);
    }

    private InstanceCapabilityVo library(Connection conn, String name, String label) throws Exception {
        String libraries = settingValue(conn, "shared_preload_libraries") + ","
                + settingValue(conn, "session_preload_libraries");
        boolean enabled = libraries.toLowerCase().contains(name);
        return InstanceCapabilityVo.of("library." + name, label, enabled ? AVAILABLE : LIMITED,
                enabled ? null : "未在 shared_preload_libraries 或 session_preload_libraries 中启用 " + name);
    }

    private InstanceCapabilityVo setting(Connection conn, String name, String label) throws Exception {
        String value = settingValue(conn, name);
        boolean enabled = "on".equalsIgnoreCase(value) || "auto".equalsIgnoreCase(value);
        return InstanceCapabilityVo.of("setting." + name, label, enabled ? AVAILABLE : LIMITED,
                enabled ? null : name + "=" + value + "，相关耗时或 Query ID 数据可能不可用");
    }

    private InstanceCapabilityVo monitorVisibility(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT rolsuper
                       OR pg_has_role(current_user, 'pg_monitor', 'member')
                       OR pg_has_role(current_user, 'pg_read_all_stats', 'member')
                  FROM pg_roles WHERE rolname = current_user
                """);
             ResultSet rs = ps.executeQuery()) {
            boolean visible = rs.next() && rs.getBoolean(1);
            return InstanceCapabilityVo.of("role.pg_monitor", "活动、SQL 文本与复制统计可见性",
                    visible ? AVAILABLE : PERMISSION_DENIED,
                    visible ? null : "采集账号缺少跨用户统计可见权限，最小授权建议：GRANT pg_monitor TO <monitor_user>");
        }
    }

    private String settingValue(Connection conn, String name) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT setting FROM pg_settings WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null ? rs.getString(1) : "";
            }
        }
    }

    private static Connection open(CollectTargetVo target) throws Exception {
        DriverManager.setLoginTimeout(5);
        String url = target.getUrlTemplate()
                .replace("{host}", target.getHost())
                .replace("{port}", String.valueOf(target.getPort()))
                .replace("{database}", StringUtils.hasText(target.getDatabaseName())
                        ? target.getDatabaseName() : "postgres");
        return DriverManager.getConnection(url, target.getConnUser(), target.getConnPassword());
    }

    private static int major(String version) {
        if (!StringUtils.hasText(version)) return -1;
        try {
            return Integer.parseInt(normalizeVersion(version).split("\\.")[0]);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int minor(String version) {
        try {
            String[] parts = version.split("\\.");
            return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String normalizeVersion(String version) {
        if (!StringUtils.hasText(version)) return "未知";
        String value = version.trim().split("\\s+")[0];
        return value.replaceAll("[^0-9.].*$", "");
    }

    private static boolean isPermissionDenied(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return message.contains("permission denied") || message.contains("must be superuser");
    }

    private static String friendly(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (isPermissionDenied(e)) {
            return "采集账号权限不足，建议授予 pg_monitor 后重试";
        }
        return "实时探测失败：" + (message.length() > 200 ? message.substring(0, 200) : message);
    }
}