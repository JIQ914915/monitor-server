package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.PgAdvisorVo;
import com.lzzh.monitor.api.response.PgObjectAnalysisVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PostgreSqlAdvisorService {
    private static final int MAX_DATABASES = 8;
    private static final long OBJECT_BUDGET_MS = 20_000;

    List<PgAdvisorVo> vacuum(CollectTargetVo target) {
        List<PgAdvisorVo> result = new ArrayList<>();
        String sql = """
                SELECT n.nspname,c.relname,c.relkind,pg_total_relation_size(c.oid) size_bytes,
                       s.n_live_tup,s.n_dead_tup,s.n_mod_since_analyze,
                       s.last_vacuum,s.last_autovacuum,s.last_analyze,s.last_autoanalyze,
                       s.vacuum_count,s.autovacuum_count,s.analyze_count,s.autoanalyze_count,
                       CASE WHEN c.relkind IN ('r','m') THEN age(c.relfrozenxid) ELSE 0 END xid_age,
                       CASE WHEN c.relkind IN ('r','m') THEN mxid_age(c.relminmxid) ELSE 0 END mxid_age,
                       array_to_string(c.reloptions,',') reloptions
                  FROM pg_stat_user_tables s
                  JOIN pg_class c ON c.oid=s.relid JOIN pg_namespace n ON n.oid=c.relnamespace
                 ORDER BY s.n_dead_tup DESC LIMIT 500
                """;
        try (Connection conn = open(target, target.getDatabaseName());
             Statement st = statement(conn); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                long live = rs.getLong("n_live_tup"), dead = rs.getLong("n_dead_tup");
                double deadPct = dead * 100d / Math.max(1, live + dead);
                long xidAge = rs.getLong("xid_age");
                boolean parent = "p".equals(rs.getString("relkind"));
                if (deadPct < 10 && xidAge < 100_000_000 && !parent) continue;
                PgAdvisorVo advice = advice("vacuum", target.getDatabaseName(),
                        rs.getString("nspname") + "." + rs.getString("relname"),
                        xidAge >= 150_000_000 || deadPct >= 30 ? "critical" : "warning");
                advice.setObservationWindow("pg_stat_user_tables 自 stats_reset 起累计，当前时点");
                advice.setEvidence("dead tuple=" + dead + "（" + String.format(Locale.ROOT, "%.1f", deadPct)
                        + "%），XID age=" + xidAge + "，对象大小=" + rs.getLong("size_bytes") + " bytes");
                advice.setAction(parent
                        ? "分区父表需单独安排定期 ANALYZE，并检查各分区统计新鲜度"
                        : xidAge >= 100_000_000
                        ? "优先处理阻塞 Vacuum 的长事务/复制槽，再评估常规 VACUUM FREEZE"
                        : "评估降低表级 autovacuum scale factor/threshold，并核对 worker 与 cost limit");
                advice.setRisk("高峰期手工 VACUUM 会增加 I/O；平台不自动执行 VACUUM FULL");
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("liveTuples", live); details.put("deadTuples", dead);
                details.put("deadPercent", deadPct); details.put("xidAge", xidAge);
                details.put("mxidAge", rs.getLong("mxid_age"));
                details.put("modifiedSinceAnalyze", rs.getLong("n_mod_since_analyze"));
                details.put("reloptions", rs.getString("reloptions"));
                details.put("lastAutovacuum", rs.getObject("last_autovacuum"));
                details.put("lastAutoanalyze", rs.getObject("last_autoanalyze"));
                advice.setDetails(details);
                result.add(advice);
            }
        } catch (Exception e) {
            throw error("Vacuum Advisor 查询失败", e);
        }
        return result;
    }

    List<PgAdvisorVo> index(CollectTargetVo target) {
        List<PgAdvisorVo> result = new ArrayList<>();
        String duplicates = """
                SELECT n.nspname,a.relname index_a,b.relname index_b,
                       pg_get_indexdef(i1.indexrelid) def_a,pg_get_indexdef(i2.indexrelid) def_b,
                       pg_relation_size(i1.indexrelid) size_a,
                       (i1.indisprimary OR i1.indisunique OR con1.oid IS NOT NULL
                         OR i1.indisreplident OR EXISTS (
                           SELECT 1 FROM pg_constraint fk
                            WHERE fk.contype='f' AND fk.conrelid=i1.indrelid
                              AND i1.indkey::smallint[] @> fk.conkey
                         )) protected_a
                  FROM pg_index i1 JOIN pg_index i2
                    ON i1.indrelid=i2.indrelid AND i1.indexrelid<i2.indexrelid
                  JOIN pg_class t ON t.oid=i1.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                  JOIN pg_class a ON a.oid=i1.indexrelid JOIN pg_class b ON b.oid=i2.indexrelid
                  LEFT JOIN pg_constraint con1 ON con1.conindid=i1.indexrelid
                 WHERE n.nspname NOT IN ('pg_catalog','information_schema')
                   AND i1.indpred IS NOT DISTINCT FROM i2.indpred
                   AND i1.indexprs IS NOT DISTINCT FROM i2.indexprs
                   AND ((i1.indkey::smallint[]=i2.indkey::smallint[])
                     OR ((i2.indkey::smallint[])[0:array_length(i1.indkey::smallint[],1)-1]
                         =i1.indkey::smallint[]))
                 LIMIT 300
                """;
        try (Connection conn = open(target, target.getDatabaseName()); Statement st = statement(conn)) {
            try (ResultSet rs = st.executeQuery(duplicates)) {
                while (rs.next()) {
                    boolean protectedIndex = rs.getBoolean("protected_a");
                    PgAdvisorVo advice = advice("duplicate_index_candidate", target.getDatabaseName(),
                            rs.getString("nspname") + "." + rs.getString("index_a"),
                            protectedIndex ? "info" : "warning");
                    advice.setObservationWindow("当前索引定义与约束目录快照");
                    advice.setEvidence(rs.getString("index_a") + " 与 " + rs.getString("index_b")
                            + " 列集合相同或左前缀重叠；容量 " + rs.getLong("size_a") + " bytes");
                    advice.setAction(protectedIndex
                            ? "受主键、唯一/外键约束或复制标识保护，不建议删除"
                            : "候选：结合 idx_scan、写入维护成本和执行计划人工确认是否保留");
                    advice.setRisk("不自动 DROP INDEX；删除前必须验证外键、复制标识和回滚方案");
                    advice.setDetails(Map.of("definitionA", rs.getString("def_a"),
                            "definitionB", rs.getString("def_b"), "protected", protectedIndex));
                    result.add(advice);
                }
            }
            try (ResultSet rs = st.executeQuery("""
                    SELECT schemaname,relname,seq_scan,seq_tup_read,idx_scan
                      FROM pg_stat_user_tables
                     WHERE seq_scan>100 AND seq_tup_read>100000
                       AND COALESCE(idx_scan,0)<seq_scan
                     ORDER BY seq_tup_read DESC LIMIT 100
                    """)) {
                while (rs.next()) {
                    PgAdvisorVo advice = advice("missing_index_candidate", target.getDatabaseName(),
                            rs.getString("schemaname") + "." + rs.getString("relname"), "warning");
                    advice.setObservationWindow("pg_stat_user_tables 自 stats_reset 起累计");
                    advice.setEvidence("seq_scan=" + rs.getLong("seq_scan") + "，seq_tup_read="
                            + rs.getLong("seq_tup_read") + "，idx_scan=" + rs.getLong("idx_scan"));
                    advice.setAction("候选：结合高耗时 SQL 的过滤、Join、排序列设计索引并用 EXPLAIN 验证");
                    advice.setRisk("扫描热点不等于缺索引；低选择性、小表和批处理可能应保留顺序扫描");
                    advice.setDetails(Map.of());
                    result.add(advice);
                }
            }
        } catch (Exception e) {
            throw error("Index Advisor 查询失败", e);
        }
        return result;
    }

    PageResult<PgObjectAnalysisVo> objects(CollectTargetVo target,long requestedOffset,int limit) {
        List<String> databaseNames=databases(target).stream().limit(MAX_DATABASES).toList();
        List<DatabaseObjectCount> counts=new ArrayList<>();
        long total=0;
        for(String database:databaseNames){
            long count=countObjects(target,database,database.equals(target.getDatabaseName()));
            counts.add(new DatabaseObjectCount(database,count));total+=count;
        }
        if(total==0||requestedOffset>=total)return PageResult.of(List.of(),total);
        long offset=requestedOffset;List<PgObjectAnalysisVo> rows=new ArrayList<>();
        for(DatabaseObjectCount entry:counts){
            if(rows.size()>=limit)break;
            if(offset>=entry.count()){offset-=entry.count();continue;}
            int fetch=Math.min(limit-rows.size(),(int)Math.min(Integer.MAX_VALUE,entry.count()-offset));
            rows.addAll(queryObjects(target,entry.database(),entry.database().equals(target.getDatabaseName()),offset,fetch));
            offset=0;
        }
        return PageResult.of(rows,total);
    }

    private long countObjects(CollectTargetVo target,String database,boolean includeTablespaces){
        String sql="""
                SELECT 1
                  +(SELECT count(DISTINCT n.oid) FROM pg_namespace n JOIN pg_class c ON c.relnamespace=n.oid
                     WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND c.relkind IN ('r','p','m'))
                  +(SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                     WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND c.relkind IN ('r','p','i','I','t','m'))
                """+(includeTablespaces?"+(SELECT count(*) FROM pg_tablespace)":"");
        try(Connection conn=open(target,database);Statement st=statement(conn);ResultSet rs=st.executeQuery(sql)){
            return rs.next()?rs.getLong(1):0;
        }catch(Exception e){throw error("对象容量计数失败（"+database+"）",e);}
    }

    private List<PgObjectAnalysisVo> queryObjects(CollectTargetVo target,String database,boolean includeTablespaces,long offset,int limit){
        String union="""
                SELECT NULL::text schema_name,'database'::text object_type,current_database()::text object_name,
                       NULL::text parent_name,NULL::text tablespace,pg_database_size(current_database())::bigint size_bytes,
                       NULL::bigint estimated_rows,NULL::bigint sequential_scans,NULL::double precision cache_hit_rate
                UNION ALL
                SELECT n.nspname,'schema',n.nspname,NULL,NULL,sum(pg_total_relation_size(c.oid))::bigint,NULL,NULL,NULL
                  FROM pg_namespace n JOIN pg_class c ON c.relnamespace=n.oid
                 WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND c.relkind IN ('r','p','m')
                 GROUP BY n.nspname
                UNION ALL
                SELECT n.nspname,
                       CASE c.relkind WHEN 'r' THEN 'table' WHEN 'p' THEN 'partitioned_table'
                         WHEN 'i' THEN 'index' WHEN 'I' THEN 'partitioned_index' WHEN 't' THEN 'toast'
                         WHEN 'm' THEN 'materialized_view' ELSE c.relkind::text END,
                       c.relname,pn.nspname||'.'||pc.relname,COALESCE(ts.spcname,'pg_default'),
                       pg_total_relation_size(c.oid)::bigint,c.reltuples::bigint,sut.seq_scan::bigint,
                       CASE WHEN COALESCE(sio.heap_blks_hit,0)+COALESCE(sio.heap_blks_read,0)>0
                         THEN 100.0*sio.heap_blks_hit/(sio.heap_blks_hit+sio.heap_blks_read) END
                  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  LEFT JOIN pg_inherits inh ON inh.inhrelid=c.oid LEFT JOIN pg_class pc ON pc.oid=inh.inhparent
                  LEFT JOIN pg_namespace pn ON pn.oid=pc.relnamespace LEFT JOIN pg_tablespace ts ON ts.oid=c.reltablespace
                  LEFT JOIN pg_stat_user_tables sut ON sut.relid=c.oid LEFT JOIN pg_statio_user_tables sio ON sio.relid=c.oid
                 WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND c.relkind IN ('r','p','i','I','t','m')
                """+(includeTablespaces?"""
                UNION ALL
                SELECT NULL,'tablespace',spcname,NULL,spcname,pg_tablespace_size(oid)::bigint,NULL,NULL,NULL FROM pg_tablespace
                """:"");
        String sql="SELECT * FROM ("+union+") objects ORDER BY size_bytes DESC,object_type,schema_name NULLS FIRST,object_name LIMIT ? OFFSET ?";
        List<PgObjectAnalysisVo> rows=new ArrayList<>();
        try(Connection conn=open(target,database);PreparedStatement ps=conn.prepareStatement(sql)){
            ps.setQueryTimeout(15);ps.setInt(1,limit);ps.setLong(2,offset);
            try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                PgObjectAnalysisVo vo=new PgObjectAnalysisVo();vo.setDatabase(database);vo.setSchema(rs.getString("schema_name"));
                vo.setObjectType(rs.getString("object_type"));vo.setObjectName(rs.getString("object_name"));
                vo.setParentName(rs.getString("parent_name"));vo.setTablespace(rs.getString("tablespace"));vo.setSizeBytes(rs.getLong("size_bytes"));
                long estimated=rs.getLong("estimated_rows");if(!rs.wasNull())vo.setEstimatedRows(estimated);
                long scans=rs.getLong("sequential_scans");if(!rs.wasNull())vo.setSequentialScans(scans);
                double hit=rs.getDouble("cache_hit_rate");if(!rs.wasNull())vo.setCacheHitRate(hit);rows.add(vo);
            }}
            return rows;
        }catch(Exception e){throw error("对象容量分页查询失败（"+database+"）",e);}
    }

    private record DatabaseObjectCount(String database,long count){}
    private List<String> databases(CollectTargetVo target) {
        String scope = StringUtils.hasText(target.getPgObjectScope()) ? target.getPgObjectScope() : "monitoring";
        if ("selected".equals(scope)) {
            return target.getPgObjectDatabases() == null ? List.of() : target.getPgObjectDatabases();
        }
        if (!"all".equals(scope)) return List.of(target.getDatabaseName());
        List<String> names = new ArrayList<>();
        try (Connection conn = open(target, target.getDatabaseName()); Statement st = statement(conn);
             ResultSet rs = st.executeQuery("""
                     SELECT datname FROM pg_database
                      WHERE datallowconn AND NOT datistemplate
                        AND has_database_privilege(current_user,datname,'CONNECT')
                      ORDER BY datname
                     """)) {
            while (rs.next()) names.add(rs.getString(1));
            return names;
        } catch (Exception e) {
            throw error("多数据库发现失败", e);
        }
    }

    private static PgAdvisorVo advice(String category, String database, String object, String severity) {
        PgAdvisorVo vo = new PgAdvisorVo();
        vo.setCategory(category); vo.setDatabase(database);
        vo.setObjectName(object); vo.setSeverity(severity);
        return vo;
    }

    private static PgObjectAnalysisVo addObject(List<PgObjectAnalysisVo> out, String database,
                                                 String schema, String type, String name,
                                                 String parent, String tablespace, long size) {
        PgObjectAnalysisVo vo = new PgObjectAnalysisVo();
        vo.setDatabase(database); vo.setSchema(schema); vo.setObjectType(type);
        vo.setObjectName(name); vo.setParentName(parent); vo.setTablespace(tablespace);
        vo.setSizeBytes(size); out.add(vo); return vo;
    }

    private static Statement statement(Connection conn) throws Exception {
        Statement st = conn.createStatement(); st.setQueryTimeout(15); st.setMaxRows(2000); return st;
    }

    private static Connection open(CollectTargetVo target, String database) throws Exception {
        DriverManager.setLoginTimeout(5);
        String url = target.getUrlTemplate().replace("{host}", target.getHost())
                .replace("{port}", String.valueOf(target.getPort()))
                .replace("{database}", StringUtils.hasText(database) ? database : "postgres");
        return DriverManager.getConnection(url, target.getConnUser(), target.getConnPassword());
    }

    private static long databaseSize(Connection conn, String database) throws Exception {
        try (var ps = conn.prepareStatement("SELECT pg_database_size(?)")) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
        }
    }

    private static BusinessException error(String prefix, Exception e) {
        String message = root(e);
        if (message.toLowerCase().contains("permission denied")) {
            return new BusinessException(prefix + "：权限不足，建议授予 pg_monitor");
        }
        return new BusinessException(prefix + "：" + message);
    }

    private static String root(Throwable e) {
        Throwable current=e;
        while(current.getCause()!=null && current.getCause()!=current) current=current.getCause();
        return current.getMessage()==null?current.getClass().getSimpleName():current.getMessage();
    }
}