package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.InstanceCapabilityVo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** 只读、短连接的 MySQL 实时能力探测；不修改目标库配置或授权。 */
@Component
public class MySqlCapabilityProbe {
    private static final String AVAILABLE = "available";
    private static final String LIMITED = "limited";
    private static final String PERMISSION_DENIED = "permission_denied";
    private static final String VERSION_NOT_SUPPORT = "version_not_support";
    private static final String COLLECT_ERROR = "collect_error";

    public List<InstanceCapabilityVo> probe(CollectTargetVo target) {
        List<InstanceCapabilityVo> result = new ArrayList<>();
        try (Connection conn = open(target)) {
            conn.setReadOnly(true);
            String version = scalar(conn, "SELECT VERSION()");
            String distribution = scalar(conn, "SELECT @@version_comment");
            result.add(version(version, distribution));
            boolean ps = "1".equals(scalar(conn, "SELECT @@performance_schema"));
            result.add(InstanceCapabilityVo.of("performance_schema", "Performance Schema",
                    ps ? AVAILABLE : LIMITED, ps ? null : "Performance Schema 未启用，等待、Top SQL、MDL 与安全诊断将受限"));
            result.add(schema(conn, "sys", "sys Schema"));
            result.add(table(conn, "performance_schema", "metadata_locks", "Metadata Lock 诊断", atLeast(version, 5, 7)));
            result.add(table(conn, "performance_schema", "replication_applier_status_by_worker", "复制 Worker 诊断", atLeast(version, 5, 7)));
            result.add(table(conn, "performance_schema", "error_log", "结构化错误日志", atLeast(version, 8, 0, 22)));
            result.add(query(conn, "SELECT ID FROM information_schema.processlist LIMIT 0", "process_visibility", "会话与 SQL 可见性",
                    "采集账号缺少 PROCESS 权限，只能看到自身会话"));
            result.add(query(conn, atLeast(version, 8, 0, 22) ? "SHOW REPLICA STATUS" : "SHOW SLAVE STATUS",
                    "replication_client", "复制状态可见性", "采集账号缺少 REPLICATION CLIENT 权限"));
        } catch (Exception e) {
            result.add(InstanceCapabilityVo.of("mysql_live_probe", "MySQL 实时能力探测",
                    permission(e) ? PERMISSION_DENIED : COLLECT_ERROR, friendly(e)));
        }
        return result;
    }

    private InstanceCapabilityVo version(String version, String distribution) {
        String status = supported(version) ? AVAILABLE : VERSION_NOT_SUPPORT;
        String message = supported(version) ? "运行版本 " + clean(version) + "；发行版 " + clean(distribution)
                : "平台正式支持 MySQL 5.6、5.7、8.0、8.4，当前运行版本为 " + clean(version);
        return InstanceCapabilityVo.of("mysql_version", "MySQL 版本与发行版", status, message);
    }

    private InstanceCapabilityVo schema(Connection conn, String schema, String label) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=?")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                boolean exists = rs.next() && rs.getInt(1) > 0;
                return InstanceCapabilityVo.of("sys_schema", label, exists ? AVAILABLE : LIMITED,
                        exists ? null : "目标实例未安装 sys Schema，部分诊断将回退到 Performance Schema 原始表");
            }
        }
    }

    private InstanceCapabilityVo table(Connection conn, String schema, String table, String label,
                                       boolean versionSupported) throws Exception {
        if (!versionSupported) {
            return InstanceCapabilityVo.of(table, label, VERSION_NOT_SUPPORT, "当前 MySQL 版本不提供该系统表");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?")) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                boolean exists = rs.next() && rs.getInt(1) > 0;
                return InstanceCapabilityVo.of(table, label, exists ? AVAILABLE : LIMITED,
                        exists ? null : "系统表 " + schema + "." + table + " 不可用或未启用对应 Instrument");
            }
        }
    }

    private InstanceCapabilityVo query(Connection conn, String sql, String code, String label,
                                       String deniedMessage) {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5); st.setMaxRows(1); st.execute(sql);
            return InstanceCapabilityVo.of(code, label, AVAILABLE, null);
        } catch (Exception e) {
            return InstanceCapabilityVo.of(code, label, permission(e) ? PERMISSION_DENIED : LIMITED,
                    permission(e) ? deniedMessage : "能力探测失败：" + truncate(root(e), 160));
        }
    }

    private static Connection open(CollectTargetVo target) throws Exception {
        DriverManager.setLoginTimeout(5);
        String url = target.getUrlTemplate().replace("{host}", value(target.getHost()))
                .replace("{port}", target.getPort() == null ? "" : String.valueOf(target.getPort()))
                .replace("{database}", StringUtils.hasText(target.getDatabaseName()) ? target.getDatabaseName() : "mysql");
        return DriverManager.getConnection(url, target.getConnUser(), target.getConnPassword());
    }

    private static String scalar(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) { st.setQueryTimeout(5);
            try (ResultSet rs = st.executeQuery(sql)) { return rs.next() ? value(rs.getString(1)) : ""; }
        }
    }
    static boolean supported(String version) {
        return version != null && (version.startsWith("5.6") || version.startsWith("5.7")
                || version.startsWith("8.0") || version.startsWith("8.4"));
    }
    static boolean atLeast(String version, int major, int minor, int... patch) {
        int[] value = numbers(version); int wantedPatch = patch.length == 0 ? 0 : patch[0];
        return value[0] > major || value[0] == major && (value[1] > minor || value[1] == minor && value[2] >= wantedPatch);
    }
    private static int[] numbers(String version) {
        int[] out = {0,0,0}; if (version == null) return out;
        String[] parts = version.replaceAll("[^0-9.].*$", "").split("\\.");
        for (int i=0;i<Math.min(3,parts.length);i++) try { out[i]=Integer.parseInt(parts[i]); } catch (Exception ignored) { }
        return out;
    }
    private static boolean permission(Exception e) { String m=root(e).toLowerCase(); return m.contains("denied") || m.contains("privilege"); }
    private static String friendly(Exception e) { return permission(e) ? "采集失败：账号缺少能力探测所需的只读权限" : "实时探测失败：" + truncate(root(e),200); }
    private static String root(Throwable e) { Throwable c=e; while(c.getCause()!=null&&c.getCause()!=c)c=c.getCause(); return c.getMessage()==null?c.getClass().getSimpleName():c.getMessage(); }
    private static String truncate(String v,int n){return v==null?null:v.length()<=n?v:v.substring(0,n);}
    private static String clean(String v){return truncate(value(v).replaceAll("[\\r\\n]"," "),120);}
    private static String value(String v){return v==null?"":v;}
}
