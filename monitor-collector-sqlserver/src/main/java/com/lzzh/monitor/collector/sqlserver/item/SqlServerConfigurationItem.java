package com.lzzh.monitor.collector.sqlserver.item;
import com.lzzh.monitor.collector.spi.model.CollectRequest;import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;import com.lzzh.monitor.common.enums.CollectFrequency;import org.springframework.stereotype.Component;import java.sql.*;import java.util.Set;
/** 关键配置稳定快照；文本写入层按哈希避免相同快照重复存储。 */
@Component public class SqlServerConfigurationItem implements SqlServerMetricItem{
 public String code(){return "configuration";}public Set<CollectFrequency> frequencies(){return Set.of(CollectFrequency.DAILY);}
 public void collect(Connection c,CollectRequest r,SqlServerVersionAdapter a,SqlServerMetricSink s)throws Exception{long ts=System.currentTimeMillis();StringBuilder b=new StringBuilder();int shrink=0;try(Statement st=c.createStatement();ResultSet x=st.executeQuery(a.configurationSnapshotSql())){while(x.next()){String n=x.getString("name"),v=x.getString("value_text"),scope=x.getString("scope_name");b.append(scope).append('|').append(n).append('=').append(v).append('\n');if("database.auto_shrink".equals(n)&&"1".equals(v))shrink++;}}s.addText("sqlserver.configuration.snapshot",b.toString(),ts);s.addNumeric("sqlserver.configuration.auto_shrink_databases",shrink,ts);}
}
