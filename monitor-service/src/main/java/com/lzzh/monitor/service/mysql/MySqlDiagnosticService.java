package com.lzzh.monitor.service.mysql;

import com.lzzh.monitor.api.request.MySqlConfigDriftRequest;
import com.lzzh.monitor.api.request.MySqlCorrelationRequest;
import com.lzzh.monitor.api.request.MySqlSecurityBaselineRequest;
import com.lzzh.monitor.api.response.CapacityForecastVo;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.MySqlDiagnosticMapper;
import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import com.lzzh.monitor.dao.ts.TsMetricTrendDao;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceService;
import com.lzzh.monitor.service.metric.MetricQueryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** MySQL P0/P1 只读诊断编排：复用历史指标，深查询使用短连接、超时与行数上限。 */
@Service
public class MySqlDiagnosticService {

    @Resource private DataScopeService dataScopeService;
    @Resource private InstanceService instanceService;
    @Resource private DbInstanceMapper instanceMapper;
    @Resource private MySqlDiagnosticMapper mapper;
    @Resource private MetricQueryService metricQueryService;
    @Resource private TsMetricLatestDao latestDao;
    @Resource private TsMetricTrendDao trendDao;

    public Map<String,Object> capacityRisks(Long instanceId) {
        requireTarget(instanceId);
        CapacityForecastVo forecast = metricQueryService.capacityForecast(instanceId);
        Map<String,Double> hourly = latestDao.latestFrom1h(instanceId,
                List.of("mysql.binlog.total_bytes", "mysql.capacity.binlog_file_count"));
        Map<String,Double> daily = latestDao.latestFrom1d(instanceId,
                List.of("mysql.capacity.auto_increment_max_usage_pct", "mysql.var.binlog_expire_logs_seconds", "mysql.var.expire_logs_days"));
        List<Map<String,Object>> auto = mapper.selectAutoIncrementRisks(instanceId, 100).stream().map(row -> {
            Map<String,Object> item = new LinkedHashMap<>(row); double usage = number(row.get("value"));
            item.put("riskLevel", usage >= 90 ? "high" : usage >= 75 ? "medium" : "low");
            item.put("conclusion", usage >= 90 ? "自增 ID 接近字段上限，请尽快人工评估扩展字段类型" : "自增 ID 仍有余量");
            return item;
        }).toList();
        long now=System.currentTimeMillis();
        List<TsMetricTrendDao.TrendPoint> binlogTrend=trendDao.queryTrendByFrequency(instanceId,"mysql.binlog.total_bytes",now-30L*86400000,now,"1h");
        Double binlogDailyGrowth=growthPerDay(binlogTrend);
        String prediction = forecast.getSampleDays()==null || forecast.getSampleDays()<7 ? "insufficient"
                : forecast.getEstimatedDaysRemaining()!=null && forecast.getEstimatedDaysRemaining()<=90 ? "risk" : "stable";
        Map<String,Object> result=new LinkedHashMap<>(); result.put("status", prediction.equals("risk")?"alert":prediction.equals("insufficient")?"no_data":"normal");
        result.put("predictionStatus",prediction); result.put("forecast",forecast); result.put("autoIncrementRisks",auto); result.put("fastestGrowingTables",metricQueryService.tableGrowth(instanceId,"capacity.total_size_bytes",20).getTables());
        result.put("binlogBytes",hourly.get("mysql.binlog.total_bytes")); result.put("binlogFileCount",hourly.get("mysql.capacity.binlog_file_count"));
        result.put("binlogDailyGrowthBytes",binlogDailyGrowth);
        Double seconds=daily.get("mysql.var.binlog_expire_logs_seconds"), days=daily.get("mysql.var.expire_logs_days");
        result.put("binlogRetentionDays",binlogRetentionDays(seconds,days));
        result.put("conclusion",prediction.equals("risk")?"按当前线性增长速度，容量风险已进入 90 天关注窗口":prediction.equals("insufficient")?"历史数据不足 7 天，暂无法形成可靠预测":"容量增长处于可控范围");
        return result;
    }

    public Map<String,Object> configDrift(MySqlConfigDriftRequest request) {
        requireTarget(request.getInstanceId());
        Timestamp since=Timestamp.from(OffsetDateTime.now().minusDays(30).toInstant());
        List<Map<String,Object>> changes=new ArrayList<>(mapper.selectConfigChanges(request.getInstanceId(),since,200));
        changes.addAll(mapper.selectNumericConfigChanges(request.getInstanceId(),since,200));
        changes.sort((a,b)->Long.compare(time(b.get("collect_time")),time(a.get("collect_time"))));
        Map<String,Object> current=latestConfig(request.getInstanceId());
        List<Map<String,Object>> risks=configRisks(current,request.getBaselineTemplate());
        List<Map<String,Object>> comparison=List.of();
        if(request.getCompareInstanceId()!=null){requireTarget(request.getCompareInstanceId());comparison=compare(current,latestConfig(request.getCompareInstanceId()));}
        Map<String,Object> result=new LinkedHashMap<>(); result.put("status",risks.stream().anyMatch(r->"high".equals(r.get("riskLevel")))?"alert":risks.isEmpty()?"normal":"attention");
        result.put("changes",changes.stream().limit(200).toList());result.put("risks",risks);result.put("comparison",comparison);result.put("baselineTemplate",normalizeBaseline(request.getBaselineTemplate()));
        result.put("conclusion",changes.isEmpty()?"最近 30 天未发现关键配置变化":"最近 30 天发现 "+changes.size()+" 项关键配置变化，请结合告警时间核对");
        return result;
    }

    public Map<String,Object> replication(Long instanceId) {
        CollectTargetVo target=requireTarget(instanceId); String version=value(target.getDbVersion());
        try(Connection conn=open(target);Statement st=conn.createStatement()){
            st.setQueryTimeout(10);st.setMaxRows(1);
            Map<String,Object> status;
            try(ResultSet rs=st.executeQuery(replicaStatusSql(version))){status=rs.next()?row(rs):Map.of();}
            if(status.isEmpty()) return Map.of("status","unavailable","stage","unknown","channelStatus","UNKNOWN","conclusion","当前实例未检测到普通异步复制通道");
            List<Map<String,Object>> connections=query(conn,"SELECT CHANNEL_NAME,SERVICE_STATE,LAST_ERROR_NUMBER,LAST_ERROR_MESSAGE,LAST_HEARTBEAT_TIMESTAMP,COUNT_RECEIVED_HEARTBEATS,RECEIVED_TRANSACTION_SET FROM performance_schema.replication_connection_status ORDER BY CHANNEL_NAME LIMIT 32",32,true);
            List<Map<String,Object>> coordinators=query(conn,"SELECT CHANNEL_NAME,THREAD_ID,SERVICE_STATE,LAST_ERROR_NUMBER,LAST_ERROR_MESSAGE,LAST_ERROR_TIMESTAMP FROM performance_schema.replication_applier_status_by_coordinator ORDER BY CHANNEL_NAME LIMIT 32",32,true);
            List<Map<String,Object>> workers=query(conn,"SELECT CHANNEL_NAME,WORKER_ID,SERVICE_STATE,LAST_ERROR_NUMBER,LAST_ERROR_MESSAGE,LAST_ERROR_TIMESTAMP FROM performance_schema.replication_applier_status_by_worker ORDER BY CHANNEL_NAME,WORKER_ID LIMIT 64",64,true);
            boolean io=running(status,"Replica_IO_Running","Slave_IO_Running"),sql=running(status,"Replica_SQL_Running","Slave_SQL_Running");
            boolean connectionError=connections.stream().anyMatch(w->number(w.get("LAST_ERROR_NUMBER"))>0),coordinatorError=coordinators.stream().anyMatch(w->number(w.get("LAST_ERROR_NUMBER"))>0),workerError=workers.stream().anyMatch(w->number(w.get("LAST_ERROR_NUMBER"))>0);
            long relay=(long)number(first(status,"Relay_Log_Space")),delay=longNumber(first(status,"Seconds_Behind_Source","Seconds_Behind_Master"),-1);
            Map<String,Double> pressure=hostPressure(instanceId);boolean resourcePressure=number(pressure.get("host.cpu.usage"))>=85||number(pressure.get("host.cpu.iowait"))>=20||number(pressure.get("host.diskio.util_max"))>=80;
            String stage=!io||connectionError?"receive":!sql||coordinatorError||workerError?"apply":delay>60&&resourcePressure?"resource":relay>1024L*1024*1024&&delay>60?"queue":"normal";
            Map<String,Object> retention=sourceBinlogRetention(status,delay);
            Map<String,Object> result=new LinkedHashMap<>();result.put("status","normal".equals(stage)?"normal":"alert");result.put("stage",stage);result.put("channelStatus",io&&sql?"ON":"OFF");
            result.put("conclusion",switch(stage){case"receive"->"复制接收线程或连接异常，优先检查网络、源库 Binlog 保留和复制账号";case"apply"->"复制已接收日志，但 Coordinator、回放线程或 Worker 异常";case"resource"->"复制线程仍在运行，但主机 CPU 或磁盘压力较高，可能限制回放速度";case"queue"->"复制接收正常，但 Relay Log 积压，回放速度落后";default->"复制接收与回放均正常";});
            result.put("secondsBehind",delay<0?null:delay);result.put("secondsBehindReliable",delay>=0&&io&&sql);
            result.put("retrievedGtidSet",truncate(text(first(status,"Retrieved_Gtid_Set")),2000));result.put("executedGtidSet",truncate(text(first(status,"Executed_Gtid_Set")),2000));
            result.put("relayLogBytes",relay);result.put("lastIoError",truncate(text(first(status,"Last_IO_Error")),500));result.put("lastSqlError",truncate(text(first(status,"Last_SQL_Error")),500));
            result.put("connections",connections);result.put("coordinators",coordinators);result.put("workers",workers);result.put("hostPressure",pressure);result.putAll(retention);return result;
        }catch(Exception e){throw friendly("复制深度诊断失败",e);}
    }

    public Map<String,Object> metadataLocks(Long instanceId) {
        CollectTargetVo target=requireTarget(instanceId); if(value(target.getDbVersion()).startsWith("5.6"))return Map.of("status","unavailable","conclusion","MySQL 5.6 不提供可用的 performance_schema.metadata_locks 诊断能力","rows",List.of());
        String sql="SELECT w.OBJECT_TYPE object_type,w.OBJECT_SCHEMA object_schema,w.OBJECT_NAME object_name,w.LOCK_TYPE waiting_lock_type,"
                +"tw.PROCESSLIST_ID waiting_pid,tw.PROCESSLIST_TIME wait_seconds,LEFT(tw.PROCESSLIST_INFO,1000) waiting_query,"
                +"b.LOCK_TYPE blocking_lock_type,tb.PROCESSLIST_ID blocking_pid,LEFT(tb.PROCESSLIST_INFO,1000) blocking_query "
                +"FROM performance_schema.metadata_locks w JOIN performance_schema.threads tw ON tw.THREAD_ID=w.OWNER_THREAD_ID "
                +"LEFT JOIN performance_schema.metadata_locks b ON b.OBJECT_TYPE=w.OBJECT_TYPE AND b.OBJECT_SCHEMA <=> w.OBJECT_SCHEMA AND b.OBJECT_NAME <=> w.OBJECT_NAME AND b.LOCK_STATUS='GRANTED' AND b.OWNER_THREAD_ID<>w.OWNER_THREAD_ID "
                +"LEFT JOIN performance_schema.threads tb ON tb.THREAD_ID=b.OWNER_THREAD_ID WHERE w.LOCK_STATUS='PENDING' ORDER BY tw.PROCESSLIST_TIME DESC LIMIT 100";
        try(Connection conn=open(target)){List<Map<String,Object>> rows=query(conn,sql,100,false);long blockers=rows.stream().map(r->r.get("blocking_pid")).filter(java.util.Objects::nonNull).distinct().count();
            return Map.of("status",rows.isEmpty()?"normal":"alert","lockCategory","metadata_lock","total",rows.size(),"rootBlockers",blockers,"conclusion",rows.isEmpty()?"当前未发现 Metadata Lock 等待":"发现 "+rows.size()+" 个 Metadata Lock 等待，根阻塞会话约 "+blockers+" 个；请人工确认业务影响，平台不会自动 KILL","rows",rows);
        }catch(Exception e){throw friendly("Metadata Lock 诊断失败",e);}
    }

    public Map<String,Object> securityBaseline(MySqlSecurityBaselineRequest request) {
        CollectTargetVo target=requireTarget(request.getInstanceId());
        Map<String,Double> m1=latestDao.latestFrom1m(request.getInstanceId(),List.of("mysql.security.auth_fail_delta","mysql.security.brute_force_suspect"));
        Map<String,Double> md=latestDao.latestFrom1d(request.getInstanceId(),List.of("mysql.security.ssl_enabled","mysql.security.ssl_cert_days_left","mysql.security.audit_plugin_active","mysql.security.empty_password_count","mysql.security.anonymous_user_count","mysql.security.any_host_account_count","mysql.security.super_priv_count"));
        List<Map<String,Object>> risks=new ArrayList<>();Object ssl=md.get("mysql.security.ssl_enabled");
        addRisk(risks,ssl==null,"unknown","info","TLS 状态暂无数据","检查采集账号与 SSL 状态采集项");addRisk(risks,ssl!=null&&number(ssl)<1,"medium","risk","服务端 TLS 未启用","评估启用 TLS 并验证客户端兼容性");
        addRisk(risks,number(md.get("mysql.security.ssl_cert_days_left"))>0&&number(md.get("mysql.security.ssl_cert_days_left"))<30,"high","problem","TLS 证书将在 30 天内到期","按证书变更流程完成续期和客户端验证");
        addRisk(risks,number(m1.get("mysql.security.brute_force_suspect"))>0,"high","problem","检测到疑似暴力认证来源","核对来源地址并收紧网络与账号访问范围");
        addRisk(risks,number(md.get("mysql.security.empty_password_count"))>0,"high","problem","存在空密码账号","人工确认业务依赖后按账号变更流程修复");
        addRisk(risks,number(md.get("mysql.security.anonymous_user_count"))>0,"high","problem","存在匿名账号","人工确认后删除匿名账号");
        addRisk(risks,number(md.get("mysql.security.any_host_account_count"))>0,"medium","risk","存在 Host='%' 的宽泛授权账号","按真实应用来源收窄 Host 范围");
        addRisk(risks,number(md.get("mysql.security.super_priv_count"))>3,"medium","risk","高权限账号数量偏多","逐个核对用途并按最小权限原则收敛");
        Map<String,Object> result=new LinkedHashMap<>();result.put("mode",request.isEnhanced()?"enhanced":"basic");result.put("metrics",securityMetrics(m1,md));
        if(request.isEnhanced()){
            try(Connection conn=open(target)){
                List<Map<String,Object>> accounts=query(conn,"SELECT User user_name,Host host_pattern,account_locked,password_expired,password_lifetime,plugin FROM mysql.user ORDER BY User,Host LIMIT 500",500,false);
                List<Map<String,Object>> roles=atLeast(value(target.getDbVersion()),8,0,0)?query(conn,"SELECT USER user_name,HOST host_pattern,DEFAULT_ROLE_USER role_name,DEFAULT_ROLE_HOST role_host FROM mysql.default_roles ORDER BY USER,HOST,DEFAULT_ROLE_USER LIMIT 1000",1000,true):List.of();
                List<Map<String,Object>> grants=atLeast(value(target.getDbVersion()),8,0,0)?query(conn,"SELECT USER user_name,HOST host_pattern,PRIV privilege_name,WITH_GRANT_OPTION FROM mysql.global_grants ORDER BY USER,HOST,PRIV LIMIT 1000",1000,true):List.of();
                List<Map<String,Object>> policies=query(conn,"SHOW VARIABLES WHERE Variable_name IN ('require_secure_transport','validate_password.policy','validate_password.length','validate_password.check_user_name','default_authentication_plugin')",20,true);
                long nativePlugin=accounts.stream().filter(a->"mysql_native_password".equalsIgnoreCase(text(a.get("plugin")))).count();
                addRisk(risks,nativePlugin>0&&value(target.getDbVersion()).startsWith("8.4"),"medium","risk","MySQL 8.4 实例仍有账号使用 mysql_native_password","在测试环境验证后人工迁移至 caching_sha2_password");
                String secureTransport=variable(policies,"require_secure_transport");addRisk(risks,"OFF".equalsIgnoreCase(secureTransport),"medium","risk","未强制客户端使用安全传输","评估 require_secure_transport=ON 并先验证全部客户端");
                result.put("accounts",accounts);result.put("roles",roles);result.put("globalGrants",grants);result.put("passwordAndTlsPolicies",policies);
                Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("accounts",accounts);snapshot.put("roles",roles);snapshot.put("globalGrants",grants);snapshot.put("policies",policies);
                String snapshotJson=cn.hutool.json.JSONUtil.toJsonStr(snapshot),snapshotHash=sha256(snapshotJson),previousHash=mapper.selectLatestSecuritySnapshotHash(request.getInstanceId());boolean changed=previousHash!=null&&!previousHash.equals(snapshotHash);
                if(!snapshotHash.equals(previousHash)){Map<String,Object> record=new LinkedHashMap<>();record.put("instanceId",request.getInstanceId());record.put("snapshotHash",snapshotHash);record.put("snapshotJson",snapshotJson);record.put("changeSummary",previousHash==null?"首次增强安全基线快照":"账号、角色、全局授权或安全策略发生变化");mapper.insertSecuritySnapshot(record);}
                result.put("snapshotChanged",changed);result.put("snapshotStatus",previousHash==null?"initial":changed?"changed":"unchanged");result.put("enhancedStatus","available");
            }catch(Exception e){result.put("enhancedStatus","permission_denied");result.put("enhancedMessage","增强检查不可用：采集账号缺少 mysql.user 等安全视图的只读权限；基础检查不受影响");}
        }
        result.put("risks",risks);result.put("status",risks.stream().anyMatch(r->"high".equals(r.get("riskLevel")))?"alert":risks.isEmpty()?"normal":"attention");result.put("conclusion",risks.isEmpty()?"基础安全配置未发现明显风险":"发现 "+risks.size()+" 项安全配置关注项，所有修复均需人工确认");return result;
    }

    public Map<String,Object> correlation(MySqlCorrelationRequest request) {
        requireTarget(request.getInstanceId());long to=request.getTo()==null?System.currentTimeMillis():request.getTo(),from=request.getFrom()==null?to-2*3600000L:request.getFrom();
        if(from>=to||to-from>30L*86400000)throw new BusinessException("关联分析时间范围必须在 30 天内且开始时间早于结束时间");String frequency=correlationFrequency(from,to);
        List<String> codes=List.of("mysql.waits.io_file_ms","mysql.waits.io_table_ms","mysql.waits.lock_ms","mysql.waits.synch_ms","mysql.conn.total","mysql.innodb.lock_waits");
        Map<String,Object> trends=new LinkedHashMap<>();for(String code:codes)trends.put(code,trendDao.queryTrendByFrequency(request.getInstanceId(),code,from,to,frequency));
        DbInstance instance=instanceMapper.selectById(request.getInstanceId());if(instance!=null&&instance.getHostId()!=null){for(String code:List.of("host.cpu.usage","host.cpu.iowait","host.diskio.util_max"))trends.put(code,trendDao.queryTrendByFrequency(instance.getHostId(),code,from,to,frequency));}
        List<Map<String,Object>> top=mapper.selectTopSql(request.getInstanceId(),new Timestamp(from),new Timestamp(to),10);String dominant=dominantWait(trends);
        Map<String,Object> result=new LinkedHashMap<>();result.put("status",top.isEmpty()?"no_data":"normal");result.put("dominantWait",dominant);result.put("topSql",top);result.put("trends",trends);result.put("conclusion",top.isEmpty()?"当前窗口缺少 Top SQL 数据，无法生成确定性根因":"主要等待为 "+dominant+"；已列出同窗口主要贡献 SQL 与主机压力，请结合业务变更人工确认");return result;
    }

    private CollectTargetVo requireTarget(Long id){if(id==null||!dataScopeService.currentScope().allows(id))throw new BusinessException("无权访问该实例");CollectTargetVo t=instanceService.getCollectTarget(id);if(t==null)throw new BusinessException("实例不存在");if(!"MYSQL".equalsIgnoreCase(t.getDbType()))throw new BusinessException("该功能仅支持 MySQL 实例");return t;}
    private Connection open(CollectTargetVo t)throws Exception{DriverManager.setLoginTimeout(5);String url=t.getUrlTemplate().replace("{host}",value(t.getHost())).replace("{port}",t.getPort()==null?"":String.valueOf(t.getPort())).replace("{database}",value(t.getDatabaseName()));Connection c=DriverManager.getConnection(url,t.getConnUser(),t.getConnPassword());c.setReadOnly(true);return c;}
    private List<Map<String,Object>> query(Connection c,String sql,int limit,boolean optional)throws Exception{try(Statement st=c.createStatement()){st.setQueryTimeout(10);st.setMaxRows(limit);try(ResultSet rs=st.executeQuery(sql)){List<Map<String,Object>> out=new ArrayList<>();while(rs.next()&&out.size()<limit)out.add(row(rs));return out;}}catch(Exception e){if(optional)return List.of();throw e;}}
    private static Map<String,Object> row(ResultSet rs)throws Exception{ResultSetMetaData m=rs.getMetaData();Map<String,Object> r=new LinkedHashMap<>();for(int i=1;i<=m.getColumnCount();i++){Object v=rs.getObject(i);if(v instanceof String s)v=truncate(s,1000);r.put(m.getColumnLabel(i),v);}return r;}
    private Map<String,Object> latestConfig(Long id){Map<String,Object> out=new LinkedHashMap<>();mapper.selectConfigLatest(id).forEach(r->out.put(text(r.get("metric_code")),r.get("value_text")));mapper.selectNumericConfigLatest(id).forEach(r->out.put(text(r.get("metric_code")),r.get("value_text")));return out;}
    private List<Map<String,Object>> configRisks(Map<String,Object> c,String template){List<Map<String,Object>> r=new ArrayList<>();String baseline=normalizeBaseline(template),version=text(c.get("mysql.var_text.version"));
        if(!"observability".equals(baseline)){configRisk(r,"mysql.var_text.general_log",c,"ON","high","problem","通用查询日志已开启，可能带来明显性能与磁盘压力","确认无排障需求后走变更流程关闭");configRisk(r,"mysql.var_text.innodb_flush_log_at_trx_commit",c,"0","high","risk","事务日志未按提交刷盘，主机故障时可能不满足持久性要求","结合 RPO 与性能要求人工评估调整");configRisk(r,"mysql.var_text.sync_binlog",c,"0","medium","risk","sync_binlog=0，主机故障时可能丢失未刷盘 Binlog","结合性能与 RPO 要求评估调整");}
        if(!"observability".equals(baseline)){configRisk(r,"mysql.var_text.log_bin",c,"OFF","medium","risk","Binlog 未启用，时间点恢复与复制能力受限","结合备份与恢复策略人工评估启用");configRisk(r,"mysql.var_text.binlog_format",c,"STATEMENT","medium","risk","Binlog 使用 STATEMENT 格式，复制一致性风险较高","评估切换 ROW 并完成兼容性验证");configRisk(r,"mysql.var_text.gtid_mode",c,"OFF","low","info","GTID 未启用，复制运维与故障切换复杂度较高","在测试环境完成迁移演练后再变更");}
        if(!"replication_safety".equals(baseline)){configRisk(r,"mysql.var_text.performance_schema",c,"OFF","high","problem","Performance Schema 未启用，Top SQL、等待和锁诊断不可用","仅在维护窗口按官方步骤评估启用并重启");configNumericRisk(r,"mysql.var.performance_schema_digests_size",c,v->v<=0,"high","problem","语句摘要容量为 0，Top SQL 无法采集","评估设置非零容量并验证内存开销");configNumericRisk(r,"mysql.var.performance_schema_max_sql_text_length",c,v->v<1024,"low","info","SQL 原文采集长度偏短，长 SQL 计划分析可能被截断","结合长 SQL 比例评估调大，变更通常需要重启");}
        if(version.startsWith("5.7"))configNumericRisk(r,"mysql.var.query_cache_size",c,v->v>0,"medium","risk","MySQL 5.7 查询缓存仍占用内存且已在 8.0 移除","确认业务依赖后评估逐步关闭");return r;}
    private static String normalizeBaseline(String value){return Set.of("stability","observability","replication_safety").contains(value)?value:"stability";}
    private void configRisk(List<Map<String,Object>> out,String key,Map<String,Object> c,String expected,String level,String type,String conclusion,String suggestion){Object actual=c.get(key);if(actual!=null&&expected.equalsIgnoreCase(text(actual)))out.add(configRiskRow(key,actual,level,type,conclusion,suggestion));}
    private void configNumericRisk(List<Map<String,Object>> out,String key,Map<String,Object> c,java.util.function.DoublePredicate condition,String level,String type,String conclusion,String suggestion){Object actual=c.get(key);if(actual!=null&&condition.test(number(actual)))out.add(configRiskRow(key,actual,level,type,conclusion,suggestion));}
    private Map<String,Object> configRiskRow(String key,Object actual,String level,String type,String conclusion,String suggestion){Map<String,Object> row=new LinkedHashMap<>();row.put("parameter",key.substring(key.lastIndexOf('.')+1));row.put("riskLevel",level);row.put("adviceType",type);row.put("evidence",text(actual));row.put("applicableVersion","以目标版本官方参数定义为准");row.put("conclusion",conclusion);row.put("suggestion",suggestion);row.put("verification","变更前后使用 SHOW GLOBAL VARIABLES 复核，并观察告警与核心指标");return row;}
    private List<Map<String,Object>> compare(Map<String,Object>a,Map<String,Object>b){List<Map<String,Object>> out=new ArrayList<>();java.util.Set<String> keys=new java.util.TreeSet<>();keys.addAll(a.keySet());keys.addAll(b.keySet());for(String k:keys)if(!java.util.Objects.equals(text(a.get(k)),text(b.get(k))))out.add(Map.of("parameter",k,"currentValue",text(a.get(k)),"compareValue",text(b.get(k)),"adviceType","info"));return out.stream().limit(200).toList();}
    private static void addRisk(List<Map<String,Object>> out,boolean condition,String level,String type,String conclusion,String suggestion){if(condition)out.add(Map.of("riskLevel",level,"adviceType",type,"conclusion",conclusion,"suggestion",suggestion));}
    private Map<String,Double> hostPressure(Long instanceId){DbInstance instance=instanceMapper.selectById(instanceId);if(instance==null||instance.getHostId()==null)return Map.of();return latestDao.latestFrom1m(instance.getHostId(),List.of("host.cpu.usage","host.cpu.iowait","host.diskio.util_max"));}
    private Map<String,Object> sourceBinlogRetention(Map<String,Object> status,long delay){String sourceHost=text(first(status,"Source_Host","Master_Host"));int sourcePort=(int)longNumber(first(status,"Source_Port","Master_Port"),3306);if(sourceHost.isBlank())return Map.of("sourceRetentionStatus","unavailable","sourceRetentionConclusion","复制状态未返回源库地址，无法核对源库 Binlog 保留范围");List<DbInstance> sources=instanceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DbInstance>().eq(DbInstance::getHost,sourceHost).eq(DbInstance::getPort,sourcePort).last("LIMIT 1"));if(sources.isEmpty())return Map.of("sourceRetentionStatus","unavailable","sourceRetentionConclusion","源库 "+sourceHost+":"+sourcePort+" 未纳入平台，无法核对 Binlog 保留范围");Long sourceId=sources.getFirst().getId();Map<String,Double> retention=latestDao.latestFrom1d(sourceId,List.of("mysql.var.binlog_expire_logs_seconds","mysql.var.expire_logs_days"));Double seconds=retention.get("mysql.var.binlog_expire_logs_seconds");if(seconds==null&&retention.get("mysql.var.expire_logs_days")!=null)seconds=retention.get("mysql.var.expire_logs_days")*86400D;if(seconds==null||seconds<=0)return Map.of("sourceInstanceId",sourceId,"sourceRetentionStatus","no_data","sourceRetentionConclusion","源库缺少 Binlog 保留配置快照");boolean risk=delay>=0&&delay>=seconds*0.8;Map<String,Object> out=new LinkedHashMap<>();out.put("sourceInstanceId",sourceId);out.put("sourceBinlogRetentionSeconds",seconds.longValue());out.put("sourceRetentionStatus",risk?"risk":"stable");out.put("sourceRetentionConclusion",risk?"当前复制延迟已接近源库 Binlog 保留窗口，请优先恢复复制并人工核对日志完整性":"当前复制延迟未接近已采集的源库 Binlog 保留窗口");return out;}
    private static Map<String,Object> securityMetrics(Map<String,Double> minute,Map<String,Double> daily){Map<String,Object> out=new LinkedHashMap<>();out.put("authFailures",number(minute.get("mysql.security.auth_fail_delta")));out.put("sslEnabled",daily.get("mysql.security.ssl_enabled"));out.put("sslCertDaysLeft",daily.get("mysql.security.ssl_cert_days_left"));out.put("auditPluginActive",daily.get("mysql.security.audit_plugin_active"));out.put("emptyPasswordAccounts",daily.get("mysql.security.empty_password_count"));out.put("anonymousAccounts",daily.get("mysql.security.anonymous_user_count"));out.put("wideHostAccounts",daily.get("mysql.security.any_host_account_count"));out.put("superPrivilegeAccounts",daily.get("mysql.security.super_priv_count"));return out;}
    private static String variable(List<Map<String,Object>> rows,String name){for(Map<String,Object> row:rows){Object key=getIgnoreCase(row,"Variable_name");if(name.equalsIgnoreCase(text(key)))return text(getIgnoreCase(row,"Value"));}return "";}
    private static Object getIgnoreCase(Map<String,Object> row,String key){for(Map.Entry<String,Object> entry:row.entrySet())if(key.equalsIgnoreCase(entry.getKey()))return entry.getValue();return null;}
    private static String sha256(String value)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    private static String replicaStatusSql(String version){return atLeast(version,8,0,22)?"SHOW REPLICA STATUS":"SHOW SLAVE STATUS";}
    private static boolean atLeast(String version,int major,int minor,int patch){int[] actual={0,0,0};String normalized=value(version).replaceAll("[^0-9.].*$","");String[] parts=normalized.split("\\.");for(int i=0;i<Math.min(actual.length,parts.length);i++)try{actual[i]=Integer.parseInt(parts[i]);}catch(Exception ignored){}return actual[0]>major||actual[0]==major&&(actual[1]>minor||actual[1]==minor&&actual[2]>=patch);}
    static String correlationFrequency(long from,long to){return to-from<=6L*3600000?"1m":"1h";}
    static Double binlogRetentionDays(Double seconds,Double days){if(seconds!=null)return seconds/86400D;return days;}
    private static Double growthPerDay(List<TsMetricTrendDao.TrendPoint> p){if(p.size()<2)return null;TsMetricTrendDao.TrendPoint a=p.getFirst(),b=p.getLast();double days=(b.ts()-a.ts())/86400000D;return days<=0?null:(b.value()-a.value())/days;}
    @SuppressWarnings("unchecked") private static String dominantWait(Map<String,Object> trends){String best="数据不足";double max=-1;for(String k:List.of("mysql.waits.io_file_ms","mysql.waits.io_table_ms","mysql.waits.lock_ms","mysql.waits.synch_ms")){Object raw=trends.get(k);if(!(raw instanceof List<?> list))continue;double sum=list.stream().filter(TsMetricTrendDao.TrendPoint.class::isInstance).map(TsMetricTrendDao.TrendPoint.class::cast).mapToDouble(TsMetricTrendDao.TrendPoint::value).sum();if(sum>max){max=sum;best=k;}}return best;}
    private static boolean running(Map<String,Object>m,String...keys){return "YES".equalsIgnoreCase(text(first(m,keys)));}
    private static Object first(Map<String,Object>m,String...keys){for(String k:keys)if(m.containsKey(k))return m.get(k);return null;}
    private static double number(Object v){return v instanceof Number n?n.doubleValue():v==null?0:Double.parseDouble(v.toString());}
    private static long longNumber(Object v,long fallback){try{return v==null?fallback:v instanceof Number n?n.longValue():Long.parseLong(v.toString());}catch(Exception e){return fallback;}}
    private static long time(Object v){return v instanceof java.util.Date d?d.getTime():0;}
    private static String text(Object v){return v==null?"":String.valueOf(v);}
    private static String value(String v){return v==null?"":v;}
    private static String truncate(String v,int n){return v==null?null:v.length()<=n?v:v.substring(0,n);}
    private static BusinessException friendly(String action,Exception e){String m=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();String lower=m.toLowerCase(Locale.ROOT);return new BusinessException(lower.contains("denied")?action+"：采集账号缺少只读权限":action+"："+truncate(m,200));}
}
