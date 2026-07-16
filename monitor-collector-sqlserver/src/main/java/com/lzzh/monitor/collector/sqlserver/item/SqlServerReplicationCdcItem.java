package com.lzzh.monitor.collector.sqlserver.item;
import com.lzzh.monitor.collector.spi.model.CollectRequest;import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;import com.lzzh.monitor.common.enums.CollectFrequency;import org.springframework.stereotype.Component;import java.sql.*;import java.util.Set;
/** 复制与 CDC 能力摘要；Edition 不支持时以零或采集项不可用表达。 */
@Component public class SqlServerReplicationCdcItem implements SqlServerMetricItem{
 public String code(){return "replication_cdc";}public Set<CollectFrequency> frequencies(){return Set.of(CollectFrequency.DAILY);}
 public void collect(Connection c,CollectRequest r,SqlServerVersionAdapter a,SqlServerMetricSink s)throws Exception{long ts=System.currentTimeMillis();try(Statement st=c.createStatement();ResultSet x=st.executeQuery(a.replicationCdcSql())){if(!x.next())return;s.addNumeric("sqlserver.replication.published_databases",x.getDouble("published_databases"),ts);s.addNumeric("sqlserver.replication.subscribed_databases",x.getDouble("subscribed_databases"),ts);s.addNumeric("sqlserver.cdc.enabled_databases",x.getDouble("cdc_databases"),ts);}}
}
