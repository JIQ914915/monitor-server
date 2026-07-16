package com.lzzh.monitor.collector.sqlserver.item;
import com.lzzh.monitor.collector.spi.model.CollectRequest;import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;import com.lzzh.monitor.common.enums.CollectFrequency;import org.springframework.stereotype.Component;import java.sql.*;import java.util.Set;
/** SQL Server Agent 只读摘要；不采集作业命令，也不重跑或停止作业。 */
@Component public class SqlServerAgentItem implements SqlServerMetricItem{
 public String code(){return "agent";}public Set<CollectFrequency> frequencies(){return Set.of(CollectFrequency.HOURLY);}
 public void collect(Connection c,CollectRequest r,SqlServerVersionAdapter a,SqlServerMetricSink s)throws Exception{long ts=System.currentTimeMillis();try(Statement st=c.createStatement()){st.setQueryTimeout(10);try(ResultSet x=st.executeQuery(a.agentHealthSql())){if(!x.next())return;emit(x,s,"sqlserver.agent.job_count","job_count",ts);emit(x,s,"sqlserver.agent.disabled_jobs","disabled_jobs",ts);emit(x,s,"sqlserver.agent.failed_jobs","failed_jobs",ts);emit(x,s,"sqlserver.agent.running_jobs","running_jobs",ts);}}}
 private static void emit(ResultSet r,SqlServerMetricSink s,String m,String c,long ts)throws SQLException{double v=r.getDouble(c);if(!r.wasNull())s.addNumeric(m,v,ts);}
}
