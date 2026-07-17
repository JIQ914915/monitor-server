package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;

import cn.hutool.json.JSONUtil;
import com.lzzh.monitor.api.request.SlowSqlExplainRequest;
import com.lzzh.monitor.api.request.SlowSqlPlanHistoryRequest;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.MySqlPlanHistoryVo;
import com.lzzh.monitor.api.response.SlowSqlExplainVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.mapper.MySqlDiagnosticMapper;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 人工触发的 MySQL 估算计划采集与历史；明确禁止 ANALYZE。 */
@Component
public class MySqlPlanService {
    private static final Set<String> ALLOWED = Set.of("SELECT","INSERT","UPDATE","DELETE","REPLACE","WITH","TABLE");
    @Resource private InstanceService instanceService;
    @Resource private DataScopeService dataScopeService;
    @Resource private MySqlDiagnosticMapper mapper;

    public SlowSqlExplainVo explain(SlowSqlExplainRequest request) {
        CollectTargetVo target=require(request.getInstanceId());String sql=sanitize(request.getSql());String schema=StringUtils.hasText(request.getSchemaName())?request.getSchemaName():target.getDatabaseName();
        String format=normalizeFormat(request.getPlanFormat(),target.getDbVersion());String statement=switch(format){case"json"->"EXPLAIN FORMAT=JSON ";case"tree"->"EXPLAIN FORMAT=TREE ";default->"EXPLAIN ";};
        try(Connection conn=open(target,schema);Statement st=conn.createStatement()){
            st.setQueryTimeout(10);st.setMaxRows(200);
            try(ResultSet rs=st.executeQuery(statement+sql)){
                SlowSqlExplainVo vo=read(rs,format);enrich(vo,request.getInstanceId(),schema,sql,request.isSaveHistory());return vo;
            }
        }catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException("执行计划获取失败："+root(e));}
    }

    public List<MySqlPlanHistoryVo> history(SlowSqlPlanHistoryRequest request) {
        require(request.getInstanceId());
        return mapper.selectPlanHistory(request.getInstanceId(),request.getSchemaName(),request.getSqlHash(),50).stream().map(this::toVo).toList();
    }

    private SlowSqlExplainVo read(ResultSet rs,String format)throws Exception{
        SlowSqlExplainVo vo=new SlowSqlExplainVo();vo.setPlanFormat(format);
        ResultSetMetaData meta=rs.getMetaData();List<String> columns=new ArrayList<>();for(int i=1;i<=meta.getColumnCount();i++)columns.add(meta.getColumnLabel(i));
        List<List<String>> rows=new ArrayList<>();while(rs.next()){List<String> row=new ArrayList<>();for(int i=1;i<=meta.getColumnCount();i++)row.add(rs.getString(i));rows.add(row);}vo.setColumns(columns);vo.setRows(rows);
        if("json".equals(format)){if(rows.isEmpty()||rows.getFirst().isEmpty())throw new BusinessException("目标库未返回 JSON 执行计划");vo.setPlan(JSONUtil.parse(rows.getFirst().getFirst()));}
        else if("tree".equals(format)){String tree=rows.isEmpty()?"":rows.getFirst().getFirst();vo.setPlan(Map.of("tree",tree==null?"":tree));}
        else vo.setPlan(Map.of("columns",columns,"rows",rows));
        List<Map<String,Object>> nodes=new ArrayList<>();walk(vo.getPlan(),"0",0,nodes);vo.setNodeSummary(nodes);return vo;
    }

    private void enrich(SlowSqlExplainVo vo,Long instanceId,String schema,String sql,boolean save)throws Exception{
        String sqlHash=sha(sql.replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT));String canonical=JSONUtil.toJsonStr(vo.getPlan());String planHash=sha(canonical);
        Map<String,Object> previous=mapper.selectLatestPlan(instanceId,schema,sqlHash);String previousHash=previous==null?null:text(previous.get("plan_hash"));boolean changed=previousHash!=null&&!previousHash.equals(planHash);
        String raw=canonical.toLowerCase(Locale.ROOT);boolean fullScan=raw.contains("\"access_type\":\"all\"")||raw.contains("\"access_type\": \"all\"");boolean temporary=raw.contains("using_temporary_table\":true")||raw.contains("using temporary");boolean filesort=raw.contains("using_filesort\":true")||raw.contains("using filesort");
        String risk=fullScan&&vo.getNodeSummary().size()>1?"high":fullScan||temporary||filesort?"medium":"low";List<String> findings=new ArrayList<>();if(fullScan)findings.add("存在全表扫描");if(temporary)findings.add("使用临时表");if(filesort)findings.add("使用文件排序");String conclusion=findings.isEmpty()?"未发现明显的全表扫描、临时表或文件排序风险":String.join("、",findings)+"；请结合实际数据量人工评估索引与 SQL";
        vo.setSqlHash(sqlHash);vo.setPlanHash(planHash);vo.setPreviousPlanHash(previousHash);vo.setPlanChanged(changed);vo.setPlanDiff(planDiff(previous,vo.getNodeSummary()));vo.setRiskLevel(risk);vo.setConclusion(conclusion);
        if(save){Map<String,Object> record=new HashMap<>();record.put("instanceId",instanceId);record.put("schemaName",schema);record.put("sqlHash",sqlHash);record.put("planHash",planHash);record.put("queryText",sql);record.put("planFormat",vo.getPlanFormat());record.put("planJson",canonical);record.put("summaryJson",JSONUtil.toJsonStr(vo.getNodeSummary()));record.put("riskLevel",risk);record.put("conclusion",conclusion);record.put("previousPlanHash",previousHash);record.put("planChanged",changed);record.put("capturedBy",null);mapper.insertPlan(record);}
    }

    @SuppressWarnings("unchecked") private void walk(Object value,String path,int depth,List<Map<String,Object>> out){
        if(value==null||depth>20)return;if(value instanceof cn.hutool.json.JSONObject jo)value=jo.toBean(Map.class);if(value instanceof cn.hutool.json.JSONArray ja)value=ja.toList(Object.class);
        if(value instanceof Map<?,?> map){if(map.containsKey("table_name")||map.containsKey("access_type")){Map<String,Object> node=new LinkedHashMap<>();node.put("path",path);node.put("depth",depth);copy(map,node,"table_name","table");copy(map,node,"access_type","accessType");copy(map,node,"key","key");copy(map,node,"rows_examined_per_scan","rowsExamined");copy(map,node,"rows_produced_per_join","rowsProduced");copy(map,node,"filtered","filtered");copy(map,node,"using_temporary_table","usingTemporary");copy(map,node,"using_filesort","usingFilesort");out.add(node);}for(Map.Entry<?,?> e:map.entrySet())walk(e.getValue(),path+"."+e.getKey(),depth+1,out);}
        else if(value instanceof List<?> list)for(int i=0;i<list.size();i++)walk(list.get(i),path+"."+i,depth+1,out);
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> planDiff(Map<String,Object> previous,List<Map<String,Object>> current){if(previous==null||previous.get("node_summary")==null)return List.of();List<Map<String,Object>> before;try{before=(List<Map<String,Object>>)(List<?>)JSONUtil.toList(JSONUtil.parseArray(String.valueOf(previous.get("node_summary"))),Map.class);}catch(Exception e){return List.of();}Map<String,Map<String,Object>> left=indexNodes(before),right=indexNodes(current);java.util.Set<String> paths=new java.util.TreeSet<>();paths.addAll(left.keySet());paths.addAll(right.keySet());List<Map<String,Object>> out=new ArrayList<>();for(String path:paths){Map<String,Object>a=left.get(path),b=right.get(path);if(java.util.Objects.equals(a,b))continue;Map<String,Object> diff=new LinkedHashMap<>();diff.put("path",path);diff.put("changeType",a==null?"added":b==null?"removed":"modified");for(String key:List.of("table","accessType","key","rowsExamined","filtered","usingTemporary","usingFilesort")){Object av=a==null?null:a.get(key),bv=b==null?null:b.get(key);if(!java.util.Objects.equals(av,bv)){diff.put("previous"+Character.toUpperCase(key.charAt(0))+key.substring(1),av);diff.put("current"+Character.toUpperCase(key.charAt(0))+key.substring(1),bv);}}out.add(diff);}return out;}
    private static Map<String,Map<String,Object>> indexNodes(List<Map<String,Object>> nodes){Map<String,Map<String,Object>> out=new LinkedHashMap<>();if(nodes!=null)for(Map<String,Object> node:nodes)out.put(text(node.get("path")),node);return out;}
    private static void copy(Map<?,?>s,Map<String,Object>d,String key,String target){if(s.containsKey(key))d.put(target,s.get(key));}
    private MySqlPlanHistoryVo toVo(Map<String,Object> r){MySqlPlanHistoryVo vo=new MySqlPlanHistoryVo();vo.setId(longValue(r.get("id")));vo.setSchemaName(text(r.get("schema_name")));vo.setSqlHash(text(r.get("sql_hash")));vo.setPlanHash(text(r.get("plan_hash")));vo.setPreviousPlanHash(text(r.get("previous_plan_hash")));vo.setPlanChanged(Boolean.TRUE.equals(r.get("plan_changed")));vo.setPlanFormat(text(r.get("plan_format")));vo.setRiskLevel(text(r.get("risk_level")));vo.setConclusion(text(r.get("conclusion")));vo.setCapturedBy(text(r.get("captured_by")));try{vo.setPlan(JSONUtil.parse(String.valueOf(r.get("plan_json"))));vo.setNodeSummary((List<Map<String,Object>>)(List<?>)JSONUtil.toList(JSONUtil.parseArray(String.valueOf(r.get("node_summary"))),Map.class));}catch(Exception e){vo.setNodeSummary(List.of());}Object time=r.get("captured_at");if(time instanceof OffsetDateTime o)vo.setCapturedAt(o);else if(time instanceof Timestamp t)vo.setCapturedAt(t.toLocalDateTime().atOffset(OffsetDateTime.now().getOffset()));return vo;}
    private CollectTargetVo require(Long id){if(id==null||!dataScopeService.currentScope().allows(id))throw new BusinessException("无权访问该实例");CollectTargetVo t=instanceService.getCollectTarget(id);if(t==null)throw new BusinessException("实例不存在");if(DbType.of(t.getDbType()) != DbType.MYSQL)throw new BusinessException("该功能仅支持 MySQL 实例");return t;}
    private static Connection open(CollectTargetVo t,String schema)throws Exception{DriverManager.setLoginTimeout(5);String url=t.getUrlTemplate().replace("{host}",value(t.getHost())).replace("{port}",t.getPort()==null?"":String.valueOf(t.getPort())).replace("{database}",value(schema));Connection c=DriverManager.getConnection(url,t.getConnUser(),t.getConnPassword());c.setReadOnly(true);return c;}
    static String sanitize(String raw){String sql=raw==null?"":raw.trim();while(sql.endsWith(";"))sql=sql.substring(0,sql.length()-1).trim();if(!StringUtils.hasText(sql))throw new BusinessException("SQL 为空，无法生成执行计划");if(sql.contains(";")||sql.contains("/*")||sql.contains("--"))throw new BusinessException("仅支持不含注释的单条 SQL");if(sql.endsWith("..."))throw new BusinessException("SQL 已被目标库截断，请补全后再获取执行计划");String first=sql.split("\\s+",2)[0].replaceFirst("^\\(+","").toUpperCase(Locale.ROOT);if(!ALLOWED.contains(first))throw new BusinessException("仅支持 SELECT / INSERT / UPDATE / DELETE / REPLACE 的估算计划，且不会执行 ANALYZE");return sql;}
    private static String normalizeFormat(String raw,String version){String format=value(raw).toLowerCase(Locale.ROOT);if(format.isBlank())format="json";if(!Set.of("json","tree","tabular").contains(format))throw new BusinessException("planFormat 仅支持 json/tree/tabular");if("tree".equals(format)&&!atLeast(version,8,0,16))throw new BusinessException("EXPLAIN FORMAT=TREE 需要 MySQL 8.0.16+；当前可使用 JSON 格式");return format;}
    private static boolean atLeast(String v,int a,int b,int c){int[] n={0,0,0};String[] p=value(v).replaceAll("[^0-9.].*$","").split("\\.");for(int i=0;i<Math.min(3,p.length);i++)try{n[i]=Integer.parseInt(p[i]);}catch(Exception ignored){}return n[0]>a||n[0]==a&&(n[1]>b||n[1]==b&&n[2]>=c);}
    private static String sha(String s)throws Exception{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
    private static String root(Throwable e){Throwable c=e;while(c.getCause()!=null&&c.getCause()!=c)c=c.getCause();return c.getMessage()==null?c.getClass().getSimpleName():c.getMessage();}
    private static String value(String s){return s==null?"":s;}private static String text(Object o){return o==null?null:String.valueOf(o);}private static long longValue(Object o){return o instanceof Number n?n.longValue():o==null?0:Long.parseLong(o.toString());}
}
