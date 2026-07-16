package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;
import java.sql.*;
import java.util.Set;

/** 只读 msdb 备份覆盖摘要；备份历史不等同于可恢复验证。 */
@Component
public class SqlServerBackupItem implements SqlServerMetricItem {
    @Override public String code(){return "backup";}
    @Override public Set<CollectFrequency> frequencies(){return Set.of(CollectFrequency.HOURLY);}
    @Override public void collect(Connection conn,CollectRequest request,SqlServerVersionAdapter adapter,
                                  SqlServerMetricSink sink)throws Exception{
        long ts=System.currentTimeMillis();double maxFull=0,maxLog=0;int uncovered=0,logMissing=0;
        try(Statement st=conn.createStatement()){st.setQueryTimeout(15);
            try(ResultSet rs=st.executeQuery(adapter.backupCoverageSql())){
                while(rs.next()){
                    String db=rs.getString("database_name");int dbId=rs.getInt("database_id");
                    Double full=value(rs,"full_age_hours"),diff=value(rs,"diff_age_hours"),log=value(rs,"log_age_minutes");
                    put(sink,"sqlserver.backup.full_age_hours",db,full,ts);
                    put(sink,"sqlserver.backup.diff_age_hours",db,diff,ts);
                    put(sink,"sqlserver.backup.log_age_minutes",db,log,ts);
                    put(sink,"sqlserver.backup.latest_duration_seconds",db,value(rs,"latest_duration_seconds"),ts);
                    put(sink,"sqlserver.backup.latest_size_bytes",db,value(rs,"latest_size_bytes"),ts);
                    put(sink,"sqlserver.backup.latest_compressed_bytes",db,value(rs,"latest_compressed_bytes"),ts);
                    put(sink,"sqlserver.backup.latest_has_checksum",db,value(rs,"latest_has_checksum"),ts);
                    if(dbId>4){if(full==null)uncovered++;else maxFull=Math.max(maxFull,full);
                        String model=rs.getString("recovery_model_desc");
                        if(("FULL".equals(model)||"BULK_LOGGED".equals(model))&&log==null)logMissing++;
                        if(log!=null)maxLog=Math.max(maxLog,log);}
                }
            }
        }
        sink.addNumeric("sqlserver.backup.max_full_age_hours",maxFull,ts);
        sink.addNumeric("sqlserver.backup.max_log_age_minutes",maxLog,ts);
        sink.addNumeric("sqlserver.backup.uncovered_database_count",uncovered,ts);
        sink.addNumeric("sqlserver.backup.log_missing_database_count",logMissing,ts);
        sink.addText("sqlserver.backup.readiness_notice","存在备份记录不等于备份可恢复；请定期登记人工恢复演练结果",ts);
    }
    private static Double value(ResultSet rs,String column)throws SQLException{double v=rs.getDouble(column);return rs.wasNull()?null:v;}
    private static void put(SqlServerMetricSink sink,String metric,String db,Double value,long ts){if(value!=null)sink.addObject(metric,"database",db,value,ts);}
}
