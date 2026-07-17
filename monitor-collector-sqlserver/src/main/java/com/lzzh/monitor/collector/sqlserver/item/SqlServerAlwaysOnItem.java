package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;
import java.sql.*;

/** Always On 本地副本只读健康摘要；未启用时静默跳过，不执行故障转移。 */
@Component
public class SqlServerAlwaysOnItem implements SqlServerMetricItem {
    @Override public String code(){return "always_on";}
    @Override public void collect(Connection conn,CollectRequest request,SqlServerVersionAdapter adapter,
                                  SqlServerMetricSink sink)throws Exception{if(!adapter.supportsAlwaysOn())return;
        if(!enabled(conn))return;long ts=System.currentTimeMillis();int disconnected=0,unhealthy=0,suspended=0;
        double sendKb=0,redoKb=0,sendSec=0,redoSec=0;
        try(Statement st=conn.createStatement()){st.setQueryTimeout(10);
            try(ResultSet rs=st.executeQuery(adapter.alwaysOnHealthSql())){while(rs.next()){
                String name=rs.getString("group_name")+"/"+rs.getString("database_name");
                if(!"CONNECTED".equals(rs.getString("connected_state_desc")))disconnected++;
                if(!"HEALTHY".equals(rs.getString("synchronization_health_desc")))unhealthy++;
                if(rs.getBoolean("is_suspended"))suspended++;
                double sq=rs.getDouble("log_send_queue_size"),rq=rs.getDouble("redo_queue_size");
                sendKb=Math.max(sendKb,sq);redoKb=Math.max(redoKb,rq);
                sink.addObject("sqlserver.ag.log_send_queue_kb","availability_database",name,sq,ts);
                sink.addObject("sqlserver.ag.redo_queue_kb","availability_database",name,rq,ts);
                Double ss=value(rs,"send_seconds"),rr=value(rs,"redo_seconds");
                if(ss!=null)sendSec=Math.max(sendSec,ss);if(rr!=null)redoSec=Math.max(redoSec,rr);
                sink.addObject("sqlserver.ag.role_code","availability_database",name,roleCode(rs.getString("role_desc")),ts);
            }}
        }
        sink.addNumeric("sqlserver.ag.disconnected_replicas",disconnected,ts);
        sink.addNumeric("sqlserver.ag.unhealthy_databases",unhealthy,ts);
        sink.addNumeric("sqlserver.ag.suspended_databases",suspended,ts);
        sink.addNumeric("sqlserver.ag.max_log_send_queue_kb",sendKb,ts);
        sink.addNumeric("sqlserver.ag.max_redo_queue_kb",redoKb,ts);
        sink.addNumeric("sqlserver.ag.max_send_seconds",sendSec,ts);
        sink.addNumeric("sqlserver.ag.max_redo_seconds",redoSec,ts);
    }
    private static boolean enabled(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CAST(SERVERPROPERTY('IsHadrEnabled') AS int) enabled")){return r.next()&&r.getInt(1)==1;}}
    private static Double value(ResultSet r,String c)throws SQLException{double v=r.getDouble(c);return r.wasNull()?null:v;}
    private static int roleCode(String role){return "PRIMARY".equals(role)?1:"SECONDARY".equals(role)?2:0;}
}
