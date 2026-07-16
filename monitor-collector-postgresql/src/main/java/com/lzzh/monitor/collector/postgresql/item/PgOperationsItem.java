package com.lzzh.monitor.collector.postgresql.item;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.PgOperationalEventPoint;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 逻辑/物理复制、恢复冲突与运维任务进度的分钟级只读快照。 */
@Component
public class PgOperationsItem implements PgMetricItem {
    public static final String CODE="pg_operations";
    private static final long STALL_MS=15*60*1000L;
    private final Map<String,ProgressState> progressStates=new ConcurrentHashMap<>();
    @Override public String code(){return CODE;}
    @Override public Set<CollectFrequency> frequencies(){return Set.of(CollectFrequency.MINUTE);}

    @Override public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink) {
        snapshot(conn,request,sink,"logical_replication","subscription","""
            SELECT to_jsonb(x)::text payload FROM (
              SELECT s.*,sub.subenabled,sub.subslotname FROM pg_stat_subscription s
              JOIN pg_subscription sub ON sub.oid=s.subid LIMIT 200
            ) x""", "subname", "pid");
        snapshot(conn,request,sink,"logical_replication","publication","""
            SELECT to_jsonb(x)::text payload FROM (SELECT * FROM pg_publication LIMIT 200) x""", "pubname", null);
        snapshot(conn,request,sink,"logical_replication","subscription_table_sync","""
            SELECT to_jsonb(x)::text payload FROM (
              SELECT s.subname,n.nspname,c.relname,r.srsubstate,r.srsublsn
                FROM pg_subscription_rel r JOIN pg_subscription s ON s.oid=r.srsubid
                JOIN pg_class c ON c.oid=r.srrelid JOIN pg_namespace n ON n.oid=c.relnamespace LIMIT 500
            ) x""", "relname", "srsubstate");
        snapshot(conn,request,sink,"logical_replication","subscription_conflict_stats",
                "SELECT to_jsonb(x)::text payload FROM (SELECT * FROM pg_stat_subscription_stats LIMIT 200) x",
                "subname", null);
        snapshot(conn,request,sink,"replication","replication_slot","""
            SELECT (to_jsonb(r) || jsonb_build_object(
                       'retained_wal_bytes', CASE WHEN r.restart_lsn IS NULL THEN NULL
                           ELSE pg_wal_lsn_diff(pg_current_wal_lsn(),r.restart_lsn) END
                   ))::text payload
              FROM pg_replication_slots r LIMIT 200
            """, "slot_name", "active");
        snapshot(conn,request,sink,"replication","physical_sender","""
            SELECT to_jsonb(x)::text payload FROM (
              SELECT r.*,pg_wal_lsn_diff(pg_current_wal_lsn(),r.replay_lsn) lag_bytes,
                     pg_blocking_pids(r.pid) blocked_by FROM pg_stat_replication r LIMIT 200
            ) x""", "application_name", "state");
        snapshot(conn,request,sink,"replication","wal_receiver",
                "SELECT to_jsonb(x)::text payload FROM (SELECT * FROM pg_stat_wal_receiver LIMIT 10) x",
                "slot_name", "status");
        snapshot(conn,request,sink,"replication","recovery_conflicts",
                "SELECT to_jsonb(x)::text payload FROM (SELECT * FROM pg_stat_database_conflicts) x",
                "datname", null);
        snapshot(conn,request,sink,"replication","recovery_prefetch",
                "SELECT to_jsonb(x)::text payload FROM (SELECT * FROM pg_stat_recovery_prefetch) x",
                null, null);
        snapshot(conn,request,sink,"replication","synchronous_replication_config","""
            SELECT jsonb_build_object('synchronous_standby_names',current_setting('synchronous_standby_names'),
                                      'synchronous_commit',current_setting('synchronous_commit'))::text payload""",
                null, null);
        snapshot(conn,request,sink,"backup","wal_archiver",
                "SELECT to_jsonb(x)::text payload FROM (SELECT * FROM pg_stat_archiver) x",
                null, null);
        progress(conn,request,sink,"vacuum","pg_stat_progress_vacuum","heap_blks_scanned","heap_blks_total");
        progress(conn,request,sink,"analyze","pg_stat_progress_analyze","sample_blks_scanned","sample_blks_total");
        progress(conn,request,sink,"create_index","pg_stat_progress_create_index","blocks_done","blocks_total");
        progress(conn,request,sink,"cluster","pg_stat_progress_cluster","heap_blks_scanned","heap_blks_total");
        progress(conn,request,sink,"copy","pg_stat_progress_copy","bytes_processed","bytes_total");
        progress(conn,request,sink,"base_backup","pg_stat_progress_basebackup","backup_streamed","backup_total");
    }

    private void snapshot(Connection conn,CollectRequest request,PgMetricSink sink,String category,String type,
                          String sql,String objectKey,String stateKey){
        try(Statement st=conn.createStatement()){st.setQueryTimeout(8);st.setMaxRows(500);
            try(ResultSet rs=st.executeQuery(sql)){while(rs.next()){
                JSONObject p=JSONUtil.parseObj(rs.getString("payload"));
                String object=objectKey==null?type:p.getStr(objectKey,type);
                String severity=classifySeverity(type,p,stateKey);
                emit(sink,"postgresql",category,type,severity,p.getStr("datname",p.getStr("database")),null,
                        object,null,null,type+" "+object,p,System.currentTimeMillis());
            }}
        }catch(Exception e){unavailable(sink,category,type,e);}
    }

    private void progress(Connection conn,CollectRequest request,PgMetricSink sink,String type,String view,
                          String doneKey,String totalKey){
        String sql="SELECT to_jsonb(p)||jsonb_build_object('blocked_by',pg_blocking_pids(p.pid)) payload FROM "+view+" p LIMIT 200";
        try(Statement st=conn.createStatement()){st.setQueryTimeout(8);st.setMaxRows(200);
            try(ResultSet rs=st.executeQuery(sql)){while(rs.next()){
                JSONObject p=JSONUtil.parseObj(rs.getString("payload"));
                long done=p.getLong(doneKey,0L),total=p.getLong(totalKey,0L);
                double pct=total>0?Math.min(100d,100d*done/total):0d;p.set("progress_percent",pct);
                String key=request.getInstanceId()+":"+type+":"+p.getStr("pid","")+":"+p.getStr("relid","");
                long now=System.currentTimeMillis(); ProgressState previous=progressStates.get(key);
                long changed=previous==null||previous.done!=done?now:previous.changedAt;
                progressStates.put(key,new ProgressState(done,changed,now));
                progressStates.values().removeIf(state -> now-state.lastSeen>24L*60*60*1000);
                boolean stalled=previous!=null&&now-changed>=STALL_MS;p.set("stalled",stalled);
                String object=p.getStr("relid",p.getStr("pid",type));
                emit(sink,"postgresql","progress",type,stalled?"warning":"info",p.getStr("datname"),null,
                        object,null,null,type+" "+pct+"%",p,now);
            }}
        }catch(Exception e){unavailable(sink,"progress",type,e);}
    }

    static String classifySeverity(String type,JSONObject p,String stateKey){
        if("replication_slot".equals(type)){
            long retained=p.getLong("retained_wal_bytes",0L); boolean active=p.getBool("active",false);
            if(!active&&retained>=1024L*1024*1024) return "critical"; if(!active) return "warning";
        }
        if("subscription_conflict_stats".equals(type)&&
                (p.getLong("apply_error_count",0L)>0||p.getLong("sync_error_count",0L)>0)) return "warning";
        if("wal_archiver".equals(type)&&archiveFailureIsLatest(p)) return "warning";
        if("subscription_table_sync".equals(type)&&!List.of("r","s").contains(p.getStr("srsubstate"))) return "warning";
        if(stateKey!=null){String state=String.valueOf(p.get(stateKey));
            if(("subscription".equals(type)&&p.get("pid")==null)||
               ("physical_sender".equals(type)&&!List.of("streaming","catchup").contains(state))||
               ("wal_receiver".equals(type)&&!"streaming".equals(state))) return "warning";
        }
        return "info";
    }
    static boolean archiveFailureIsLatest(JSONObject payload){
        java.time.OffsetDateTime failed=parseTime(payload.getStr("last_failed_time"));
        if(failed==null) return false;
        java.time.OffsetDateTime archived=parseTime(payload.getStr("last_archived_time"));
        return archived==null||failed.isAfter(archived);
    }
    private static java.time.OffsetDateTime parseTime(String value){
        if(value==null||value.isBlank()) return null;
        try{return java.time.OffsetDateTime.parse(value);}catch(Exception ignored){return null;}
    }    private static void emit(PgMetricSink sink,String source,String category,String type,String severity,
                             String db,String user,String object,String queryId,String sqlState,String message,
                             Map<String,Object> payload,long time){
        String redacted=PgSensitiveDataRedactor.redact(message);
        sink.addOperationalEvent(new PgOperationalEventPoint(source,category,type,severity,db,user,object,
                queryId,sqlState,redacted,PgSensitiveDataRedactor.fingerprint(category+":"+type+":"+object),
                sanitizePayload(payload),true,time));
    }
    private static Map<String,Object> sanitizePayload(Map<String,Object> payload){
        Map<String,Object> out=new LinkedHashMap<>();
        payload.forEach((key,value)->out.put(key,sanitizeValue(value)));
        return out;
    }
    private static Object sanitizeValue(Object value){
        if(value instanceof Map<?,?> map){Map<String,Object> out=new LinkedHashMap<>();map.forEach((k,v)->out.put(String.valueOf(k),sanitizeValue(v)));return out;}
        if(value instanceof Iterable<?> items){java.util.ArrayList<Object> out=new java.util.ArrayList<>();items.forEach(v->out.add(sanitizeValue(v)));return out;}
        return value instanceof String text?PgSensitiveDataRedactor.redactSecrets(text):value;
    }
    private static void unavailable(PgMetricSink sink,String category,String type,Exception e){
        String reason=e instanceof java.sql.SQLException sql?unavailableReason(sql):"collection_failed";
        emit(sink,"postgresql",category,type+"_unavailable","warning",null,null,type,null,null,
                type+" unavailable: "+reason,Map.of("reason",reason),System.currentTimeMillis());
    }
    static String unavailableReason(java.sql.SQLException error){
        return switch(String.valueOf(error.getSQLState())){
            case "42P01","42703","42883","0A000" -> "unsupported";
            case "42501" -> "permission_denied";
            default -> "collection_failed";
        };
    }    private record ProgressState(long done,long changedAt,long lastSeen){}
}