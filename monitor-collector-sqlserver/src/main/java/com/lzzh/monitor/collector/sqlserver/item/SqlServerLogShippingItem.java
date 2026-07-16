package com.lzzh.monitor.collector.sqlserver.item;
import com.lzzh.monitor.collector.spi.model.CollectRequest;import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;import com.lzzh.monitor.common.enums.CollectFrequency;import org.springframework.stereotype.Component;import java.sql.*;import java.util.Set;
/** 仅在 msdb 存在日志传送监控数据时输出，不与 Always On 混用。 */
@Component public class SqlServerLogShippingItem implements SqlServerMetricItem{
 public String code(){return "log_shipping";}public Set<CollectFrequency> frequencies(){return Set.of(CollectFrequency.HOURLY);}
 public void collect(Connection c,CollectRequest r,SqlServerVersionAdapter a,SqlServerMetricSink s)throws Exception{long ts=System.currentTimeMillis();try(Statement st=c.createStatement()){st.setQueryTimeout(10);try(ResultSet x=st.executeQuery(a.logShippingSql())){if(!x.next()||x.getInt("primary_count")+x.getInt("secondary_count")==0)return;emit(x,s,"sqlserver.log_shipping.backup_delay_minutes","max_backup_delay_minutes",ts);emit(x,s,"sqlserver.log_shipping.copy_delay_minutes","max_copy_delay_minutes",ts);emit(x,s,"sqlserver.log_shipping.restore_delay_minutes","max_restore_delay_minutes",ts);}}}
 private static void emit(ResultSet r,SqlServerMetricSink s,String m,String c,long ts)throws SQLException{s.addNumeric(m,r.getDouble(c),ts);}
}
