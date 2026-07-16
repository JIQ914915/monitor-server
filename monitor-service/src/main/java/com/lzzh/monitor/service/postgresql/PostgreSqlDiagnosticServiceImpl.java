package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.api.request.PgSessionActionRequest;
import com.lzzh.monitor.api.request.PgSessionQueryRequest;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.PgBlockingNodeVo;
import com.lzzh.monitor.api.response.PgDatabaseVo;
import com.lzzh.monitor.api.response.PgSessionVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceService;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PostgreSqlDiagnosticServiceImpl implements PostgreSqlDiagnosticService {
    private static final int QUERY_TIMEOUT_SECONDS = 10;
    private static final int MAX_SESSIONS = 500;

    @Resource private InstanceService instanceService;
    @Resource private DataScopeService dataScopeService;

    @Override
    public List<PgDatabaseVo> databases(Long instanceId) {
        CollectTargetVo target = target(instanceId);
        List<PgDatabaseVo> result = new ArrayList<>();
        try (Connection conn = open(target);
             Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            st.setMaxRows(500);
            try (ResultSet rs = st.executeQuery("""
                    SELECT datname, datallowconn,
                           has_database_privilege(current_user, datname, 'CONNECT') AS connectable,
                           pg_database_size(datname) AS size_bytes
                      FROM pg_database
                     WHERE NOT datistemplate
                     ORDER BY datname
                    """)) {
                while (rs.next()) {
                    PgDatabaseVo vo = new PgDatabaseVo();
                    vo.setName(rs.getString("datname"));
                    vo.setAllowConnections(rs.getBoolean("datallowconn"));
                    vo.setConnectable(rs.getBoolean("connectable"));
                    vo.setSizeBytes(rs.getLong("size_bytes"));
                    vo.setInScope(inScope(target, vo.getName()));
                    result.add(vo);
                }
            }
        } catch (Exception e) {
            throw diagnosticError("数据库发现失败", e);
        }
        return result;
    }

    @Override
    public PageResult<PgSessionVo> sessions(PgSessionQueryRequest request) {
        return querySessionPage(target(request.getInstanceId()), request);
    }

    @Override
    public List<PgBlockingNodeVo> blockingTree(Long instanceId) {
        CollectTargetVo target = target(instanceId);
        return buildBlockingTree(querySessions(target));
    }

    static List<PgBlockingNodeVo> buildBlockingTree(List<PgSessionVo> sessions) {
        Map<Integer, PgSessionVo> byPid = new HashMap<>();
        sessions.forEach(s -> byPid.put(s.getPid(), s));
        Map<Integer, List<Integer>> children = new LinkedHashMap<>();
        Set<Integer> blocked = new HashSet<>();
        for (PgSessionVo session : sessions) {
            for (Integer blocker : session.getBlockedBy()) {
                children.computeIfAbsent(blocker, ignored -> new ArrayList<>()).add(session.getPid());
                blocked.add(session.getPid());
            }
        }
        List<Integer> roots = children.keySet().stream().filter(pid -> !blocked.contains(pid)).toList();
        List<PgBlockingNodeVo> result = new ArrayList<>();
        for (Integer root : roots) {
            result.add(buildNode(root, byPid, children, new HashSet<>()));
        }
        result.sort(Comparator.comparingInt(PgBlockingNodeVo::getAffectedSessions).reversed());
        return result;
    }

    @Override
    public boolean cancel(PgSessionActionRequest request) {
        return act(request, "pg_cancel_backend");
    }

    @Override
    public boolean terminate(PgSessionActionRequest request) {
        return act(request, "pg_terminate_backend");
    }

    private boolean act(PgSessionActionRequest request, String function) {
        if (!StringUtils.hasText(request.getReason())) {
            throw new BusinessException("操作原因不能为空");
        }
        CollectTargetVo target = target(request.getInstanceId());
        try (Connection conn = open(target)) {
            ensureClientSession(conn, request.getPid());
            try (PreparedStatement ps = conn.prepareStatement("SELECT " + function + "(?)")) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setInt(1, request.getPid());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw diagnosticError("会话处置失败", e);
        }
    }

    private void ensureClientSession(Connection conn, int pid) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT backend_type FROM pg_stat_activity
                 WHERE pid = ? AND pid <> pg_backend_pid()
                """)) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BusinessException("目标会话不存在或已结束");
                }
                if (!"client backend".equals(rs.getString(1))) {
                    throw new BusinessException("只允许处置客户端会话");
                }
            }
        }
    }

    private PageResult<PgSessionVo> querySessionPage(CollectTargetVo target, PgSessionQueryRequest request) {
        Pages.PageWindow page=Pages.window(request);
        String duration="GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp() - COALESCE(a.xact_start, a.query_start, a.state_change))))::bigint";
        String filters="""
                 FROM pg_stat_activity a
                WHERE a.backend_type = 'client backend'
                  AND a.pid <> pg_backend_pid()
                  AND (? IS NULL OR lower(COALESCE(a.datname,'')) LIKE ?)
                  AND (? IS NULL OR lower(COALESCE(a.usename,'')) LIKE ?)
                  AND (? IS NULL OR lower(COALESCE(a.application_name,'')) LIKE ?)
                  AND (? IS NULL OR lower(COALESCE(a.state,'')) LIKE ?)
                  AND (? IS NULL OR lower(COALESCE(a.wait_event_type,'')) LIKE ?)
                  AND (? IS NULL OR %s>=?)
                """.formatted(duration);
        String countSql="SELECT count(*) "+filters;
        String dataSql="""
                SELECT a.pid, a.datname, a.usename, a.application_name,
                       a.client_addr::text AS client_addr, a.backend_type, a.state,
                       a.xact_start, a.query_start, a.state_change,
                       %s AS duration_seconds,
                       a.wait_event_type, a.wait_event, a.query_id::text AS query_id,
                       left(a.query, 4000) AS query, pg_blocking_pids(a.pid) AS blocked_by,
                       ARRAY(SELECT DISTINCT COALESCE(n.nspname || '.' || c.relname, l.locktype) || ' [' || l.mode || ']'
                               FROM pg_locks l LEFT JOIN pg_class c ON c.oid = l.relation
                               LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
                              WHERE l.pid = a.pid AND NOT l.granted) AS locked_objects,
                       (cardinality(pg_blocking_pids(a.pid))=0 AND EXISTS(
                         SELECT 1 FROM pg_stat_activity b WHERE a.pid=ANY(pg_blocking_pids(b.pid)))) AS root_blocker
                """.formatted(duration)+filters+" ORDER BY duration_seconds DESC LIMIT ? OFFSET ?";        try(Connection conn=open(target)){
            long total;
            try(PreparedStatement ps=conn.prepareStatement(countSql)){
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);bindSessionFilters(ps,request);
                try(ResultSet rs=ps.executeQuery()){total=rs.next()?rs.getLong(1):0;}
            }
            if(total==0)return PageResult.of(List.of(),0);
            List<PgSessionVo> rows=new ArrayList<>();
            try(PreparedStatement ps=conn.prepareStatement(dataSql)){
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);int next=bindSessionFilters(ps,request);
                ps.setInt(next++,page.pageSize());ps.setLong(next,page.offset());
                try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(readSession(rs,true));}
            }
            return PageResult.of(rows,total);
        }catch(Exception e){throw diagnosticError("实时会话查询失败",e);}
    }

    private static int bindSessionFilters(PreparedStatement ps,PgSessionQueryRequest request)throws Exception{
        int i=1;
        i=bindContains(ps,i,request.getDatabase());i=bindContains(ps,i,request.getUser());
        i=bindContains(ps,i,request.getApplication());i=bindContains(ps,i,request.getState());
        i=bindContains(ps,i,request.getWaitEventType());
        if(request.getMinDurationSeconds()==null){ps.setNull(i++,java.sql.Types.BIGINT);ps.setNull(i++,java.sql.Types.BIGINT);}
        else{ps.setLong(i++,request.getMinDurationSeconds());ps.setLong(i++,request.getMinDurationSeconds());}
        return i;
    }
    private static int bindContains(PreparedStatement ps,int index,String value)throws Exception{
        String normalized=StringUtils.hasText(value)?value.trim().toLowerCase():null;
        ps.setString(index++,normalized);ps.setString(index++,normalized==null?null:"%"+normalized+"%");return index;
    }
    private static PgSessionVo readSession(ResultSet rs,boolean withRoot)throws Exception{
        PgSessionVo vo=new PgSessionVo();vo.setPid(rs.getInt("pid"));vo.setDatabase(rs.getString("datname"));
        vo.setUser(rs.getString("usename"));vo.setApplication(rs.getString("application_name"));vo.setClientAddress(rs.getString("client_addr"));
        vo.setBackendType(rs.getString("backend_type"));vo.setState(rs.getString("state"));vo.setTransactionStart(offset(rs,"xact_start"));
        vo.setQueryStart(offset(rs,"query_start"));vo.setStateChange(offset(rs,"state_change"));vo.setDurationSeconds(rs.getLong("duration_seconds"));
        vo.setWaitEventType(rs.getString("wait_event_type"));vo.setWaitEvent(rs.getString("wait_event"));vo.setQueryId(rs.getString("query_id"));
        vo.setQuery(rs.getString("query"));vo.setBlockedBy(integerArray(rs.getArray("blocked_by")));vo.setLockedObjects(stringArray(rs.getArray("locked_objects")));
        if(withRoot)vo.setRootBlocker(rs.getBoolean("root_blocker"));return vo;
    }
    private List<PgSessionVo> querySessions(CollectTargetVo target) {
        List<PgSessionVo> result = new ArrayList<>();
        try (Connection conn = open(target);
             Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            st.setMaxRows(MAX_SESSIONS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT a.pid, a.datname, a.usename, a.application_name,
                           a.client_addr::text AS client_addr, a.backend_type, a.state,
                           a.xact_start, a.query_start, a.state_change,
                           GREATEST(0, EXTRACT(EPOCH FROM
                               (clock_timestamp() - COALESCE(a.xact_start, a.query_start, a.state_change))))::bigint
                               AS duration_seconds,
                           a.wait_event_type, a.wait_event, a.query_id::text AS query_id,
                           left(a.query, 4000) AS query, pg_blocking_pids(a.pid) AS blocked_by,
                           ARRAY(SELECT DISTINCT COALESCE(n.nspname || '.' || c.relname, l.locktype) || ' [' || l.mode || ']'
                                   FROM pg_locks l
                                   LEFT JOIN pg_class c ON c.oid = l.relation
                                   LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
                                  WHERE l.pid = a.pid AND NOT l.granted) AS locked_objects
                      FROM pg_stat_activity a
                     WHERE a.backend_type = 'client backend'
                       AND a.pid <> pg_backend_pid()
                     ORDER BY duration_seconds DESC
                    """)) {
                while (rs.next()) {
                    PgSessionVo vo = new PgSessionVo();
                    vo.setPid(rs.getInt("pid"));
                    vo.setDatabase(rs.getString("datname"));
                    vo.setUser(rs.getString("usename"));
                    vo.setApplication(rs.getString("application_name"));
                    vo.setClientAddress(rs.getString("client_addr"));
                    vo.setBackendType(rs.getString("backend_type"));
                    vo.setState(rs.getString("state"));
                    vo.setTransactionStart(offset(rs, "xact_start"));
                    vo.setQueryStart(offset(rs, "query_start"));
                    vo.setStateChange(offset(rs, "state_change"));
                    vo.setDurationSeconds(rs.getLong("duration_seconds"));
                    vo.setWaitEventType(rs.getString("wait_event_type"));
                    vo.setWaitEvent(rs.getString("wait_event"));
                    vo.setQueryId(rs.getString("query_id"));
                    vo.setQuery(rs.getString("query"));
                    vo.setBlockedBy(integerArray(rs.getArray("blocked_by")));
                    vo.setLockedObjects(stringArray(rs.getArray("locked_objects")));
                    result.add(vo);
                }
            }
        } catch (Exception e) {
            throw diagnosticError("实时会话查询失败", e);
        }
        return result;
    }

    private static PgBlockingNodeVo buildNode(int pid, Map<Integer, PgSessionVo> sessions,
                                       Map<Integer, List<Integer>> children, Set<Integer> path) {
        PgBlockingNodeVo node = toNode(sessions.get(pid), pid);
        if (!path.add(pid)) {
            return node;
        }
        for (Integer child : children.getOrDefault(pid, List.of())) {
            node.getChildren().add(buildNode(child, sessions, children, new HashSet<>(path)));
        }
        node.setAffectedSessions(descendantCount(node));
        return node;
    }

    private static PgBlockingNodeVo toNode(PgSessionVo session, int pid) {
        PgBlockingNodeVo node = new PgBlockingNodeVo();
        node.setPid(pid);
        if (session != null) {
            node.setDatabase(session.getDatabase());
            node.setUser(session.getUser());
            node.setApplication(session.getApplication());
            node.setClientAddress(session.getClientAddress());
            node.setState(session.getState());
            node.setDurationSeconds(session.getDurationSeconds());
            node.setWaitEventType(session.getWaitEventType());
            node.setWaitEvent(session.getWaitEvent());
            node.setQuery(session.getQuery());
            node.setLockedObjects(session.getLockedObjects());
        }
        return node;
    }

    private static int descendantCount(PgBlockingNodeVo node) {
        int count = node.getChildren().size();
        for (PgBlockingNodeVo child : node.getChildren()) {
            count += descendantCount(child);
        }
        return count;
    }

    private CollectTargetVo target(Long instanceId) {
        if (instanceId == null || !dataScopeService.currentScope().allows(instanceId)) {
            throw new BusinessException("无权访问该实例");
        }
        CollectTargetVo target = instanceService.getCollectTarget(instanceId);
        if (target == null) {
            throw new BusinessException("实例不存在");
        }
        if (!"POSTGRESQL".equalsIgnoreCase(target.getDbType())) {
            throw new BusinessException("该功能仅支持 PostgreSQL 实例");
        }
        return target;
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

    private static OffsetDateTime offset(ResultSet rs, String column) throws Exception {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private static List<Integer> integerArray(Array array) throws Exception {
        if (array == null) return List.of();
        Object raw = array.getArray();
        List<Integer> result = new ArrayList<>();
        if (raw instanceof Object[] values) {
            for (Object value : values) {
                if (value instanceof Number number) result.add(number.intValue());
            }
        }
        return result;
    }

    private static List<String> stringArray(Array array) throws Exception {
        if (array == null) return List.of();
        Object raw = array.getArray();
        List<String> result = new ArrayList<>();
        if (raw instanceof Object[] values) {
            for (Object value : values) if (value != null) result.add(value.toString());
        }
        return result;
    }

    private static boolean inScope(CollectTargetVo target, String database) {
        String scope = StringUtils.hasText(target.getPgObjectScope())
                ? target.getPgObjectScope() : "monitoring";
        return switch (scope) {
            case "all" -> true;
            case "selected" -> target.getPgObjectDatabases() != null
                    && target.getPgObjectDatabases().contains(database);
            default -> database.equals(target.getDatabaseName());
        };
    }
    private static boolean matches(String expected, String actual) {
        return !StringUtils.hasText(expected)
                || (actual != null && actual.toLowerCase().contains(expected.trim().toLowerCase()));
    }

    private static BusinessException diagnosticError(String prefix, Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (message.toLowerCase().contains("permission denied")) {
            return new BusinessException(prefix + "：采集账号权限不足，请授予 pg_monitor");
        }
        return new BusinessException(prefix + "：" + message);
    }
}