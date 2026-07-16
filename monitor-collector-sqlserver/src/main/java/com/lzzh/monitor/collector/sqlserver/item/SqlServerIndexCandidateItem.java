package com.lzzh.monitor.collector.sqlserver.item;
import com.lzzh.monitor.collector.spi.model.CollectRequest;import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;import com.lzzh.monitor.common.enums.CollectFrequency;import org.springframework.stereotype.Component;import java.sql.*;import java.util.Set;
/** 索引候选线索；DMV 观察窗自实例启动开始，只建议人工验证，不自动建删索引。 */
@Component public class SqlServerIndexCandidateItem implements SqlServerMetricItem{
 public String code(){return "index_candidates";}public Set<CollectFrequency> frequencies(){return Set.of(CollectFrequency.DAILY);}
 public void collect(Connection c,CollectRequest r,SqlServerVersionAdapter a,SqlServerMetricSink s)throws Exception{long ts=System.currentTimeMillis();int missing=0,unused=0;try(Statement st=c.createStatement()){st.setQueryTimeout(20);st.setMaxRows(100);try(ResultSet x=st.executeQuery(a.indexCandidatesSql())){while(x.next()){String type=x.getString("candidate_type");s.addObject("sqlserver.index.candidate_score","index_"+type,x.getString("object_name"),x.getDouble("score"),ts);if("missing".equals(type))missing++;else unused++;}}}s.addNumeric("sqlserver.index.missing_candidate_count",missing,ts);s.addNumeric("sqlserver.index.unused_candidate_count",unused,ts);s.addText("sqlserver.index.observation_notice","索引使用 DMV 自 SQL Server 启动后累计；短期未使用不代表可删除，建议须结合 SQL、读写成本、空间和维护窗口人工验证",ts);}
}
