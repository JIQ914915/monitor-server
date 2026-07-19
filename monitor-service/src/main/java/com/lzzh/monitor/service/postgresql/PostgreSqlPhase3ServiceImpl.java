package com.lzzh.monitor.service.postgresql;

import cn.hutool.json.JSONUtil;
import com.lzzh.monitor.api.request.PgOperationalEventQuery;

import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.api.request.PgPageRequest;
import com.lzzh.monitor.api.request.PgRestoreDrillRequest;
import com.lzzh.monitor.api.response.PgOperationalEventVo;
import com.lzzh.monitor.api.response.PgOperationalHealthVo;
import com.lzzh.monitor.api.response.PgOperationalSummaryVo;
import com.lzzh.monitor.api.response.PgRestoreDrillVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.mapper.PgOperationsMapper;
import com.lzzh.monitor.dao.mapper.SysDictItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.dao.entity.SysDictItem;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceService;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PostgreSqlPhase3ServiceImpl implements PostgreSqlPhase3Service {
    private static final Pattern SECRET=Pattern.compile("(?i)(password|token|api[_-]?key|authorization)\\s*[:=]\\s*\\S+");
    @Resource private PgOperationsMapper mapper;
    @Resource private SysDictItemMapper dictItemMapper;
    @Resource private InstanceService instanceService;
    @Resource private DataScopeService dataScopeService;

    @Override public PageResult<PgOperationalEventVo> events(PgOperationalEventQuery request,String forcedSource,String forcedCategory,boolean excludeAudit){
        requireInstance(request.getInstanceId());
        OffsetDateTime to=request.getTo()==null?OffsetDateTime.now():request.getTo();
        OffsetDateTime from=request.getFrom()==null?to.minusDays(7):request.getFrom();
        if(from.isAfter(to)) throw new BusinessException("开始时间不能晚于结束时间");
        Pages.PageWindow page=Pages.window(request);
        String source=forcedSource!=null?forcedSource:trim(request.getSource());
        String category=forcedCategory!=null?forcedCategory:trim(request.getCategory());
        String sqlState=trim(request.getSqlState()),database=trim(request.getDatabase()),user=trim(request.getUser()),keyword=trim(request.getKeyword());
        Timestamp fromTs=ts(from),toTs=ts(to);
        boolean currentSnapshot=forcedCategory!=null;
        long total=currentSnapshot
                ?mapper.countSnapshots(request.getInstanceId(),source,category,sqlState,database,user,keyword,fromTs,toTs)
                :mapper.countEvents(request.getInstanceId(),source,category,excludeAudit,sqlState,database,user,keyword,fromTs,toTs);
        if(total==0)return PageResult.of(List.of(),0);
        List<Map<String,Object>> rows=currentSnapshot
                ?mapper.selectSnapshots(request.getInstanceId(),source,category,sqlState,database,user,keyword,fromTs,toTs,page.pageSize(),page.offset())
                :mapper.selectEvents(request.getInstanceId(),source,category,excludeAudit,sqlState,database,user,keyword,fromTs,toTs,page.pageSize(),page.offset());
        return PageResult.of(rows.stream().map(this::event).toList(),total);
    }
    @Override public List<PgOperationalSummaryVo> summary(Long instanceId){
        requireInstance(instanceId);
        return mapper.selectSnapshotSummary(instanceId,ts(OffsetDateTime.now().minusDays(1))).stream()
                .map(this::summaryVo).toList();
    }
    @Override public PgOperationalHealthVo health(Long instanceId){
        List<PgOperationalSummaryVo> summaries=summary(instanceId);
        List<PgOperationalSummaryVo> risks=summaries.stream()
                .filter(item->severityRank(item.getSeverity())>0)
                .sorted(java.util.Comparator.comparingInt((PgOperationalSummaryVo item)->severityRank(item.getSeverity())).reversed()
                        .thenComparing(PgOperationalSummaryVo::getEventCount,java.util.Comparator.reverseOrder()))
                .toList();
        PgOperationalHealthVo health=new PgOperationalHealthVo();
        String severity=risks.stream().map(PgOperationalSummaryVo::getSeverity)
                .max(java.util.Comparator.comparingInt(PostgreSqlPhase3ServiceImpl::severityRank)).orElse(summaries.isEmpty()?"no_data":"info");
        health.setSeverity(severity);
        health.setConclusion(switch(severity){
            case "critical" -> "数据库存在需要立即处理的运维风险";
            case "warning" -> "数据库存在需要关注的运维风险";
            default -> summaries.isEmpty()?"最近 24 小时暂无运维数据，暂时无法判断":"数据库原生运维状态正常";
        });
        health.setRiskCount(risks.stream().mapToLong(PgOperationalSummaryVo::getEventCount).sum());
        health.setAffectedObjectCount(risks.stream().mapToLong(PgOperationalSummaryVo::getFingerprintCount).sum());
        health.setLastSeen(summaries.stream().map(PgOperationalSummaryVo::getLastSeen).filter(java.util.Objects::nonNull).max(OffsetDateTime::compareTo).orElse(null));
        health.setRisks(risks);
        return health;
    }
    @Override public PageResult<PgRestoreDrillVo> restoreDrills(PgPageRequest request){
        requireInstance(request.getInstanceId());Pages.PageWindow page=Pages.window(request);
        long total=mapper.countRestoreDrills(request.getInstanceId());
        return PageResult.of(total==0?List.of():mapper.selectRestoreDrills(request.getInstanceId(),page.pageSize(),page.offset()).stream().map(this::drill).toList(),total);
    }
    @Override public List<PgRestoreDrillVo> latestRestoreDrills(Long instanceId,int limit){
        requireInstance(instanceId);return mapper.selectRestoreDrills(instanceId,Math.min(200,Math.max(1,limit)),0).stream().map(this::drill).toList();
    }
    @Override public PgRestoreDrillVo saveRestoreDrill(PgRestoreDrillRequest request){
        requireInstance(request.getInstanceId());
        String status=trim(request.getStatus()),validation=trim(request.getValidationResult());
        requireDictValue("pg_restore_drill_status",status,"恢复演练状态");
        requireDictValue("pg_restore_validation_result",validation,"校验结果");
        if(request.getFinishedAt()!=null&&request.getFinishedAt().isBefore(request.getStartedAt())) throw new BusinessException("结束时间不能早于开始时间");
        Map<String,Object> row=new LinkedHashMap<>();row.put("instanceId",request.getInstanceId());row.put("backupId",trim(request.getBackupId()));
        row.put("targetTime",nullableTs(request.getTargetTime()));row.put("startedAt",ts(request.getStartedAt()));row.put("finishedAt",nullableTs(request.getFinishedAt()));
        row.put("status",status);row.put("validationResult",validation);row.put("durationSeconds",request.getFinishedAt()==null?null:Duration.between(request.getStartedAt(),request.getFinishedAt()).toSeconds());
        row.put("ownerName",truncate(request.getOwnerName(),128));row.put("notes",redact(truncate(request.getNotes(),4000)));row.put("createdBy",null);mapper.insertRestoreDrill(row);
        Long id=row.get("id") instanceof Number n?n.longValue():null;return mapper.selectRestoreDrills(request.getInstanceId(),200,0).stream().filter(r->id==null||longValue(r,"id")==id).map(this::drill).findFirst().orElseThrow(()->new BusinessException("恢复演练保存失败"));
    }
    private void requireDictValue(String dictType,String value,String field){
        if(value==null||dictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getDictType,dictType).eq(SysDictItem::getItemValue,value).eq(SysDictItem::getStatus,"enabled"))==0) throw new BusinessException(field+"不合法或已停用");
    }
    private void requireInstance(Long id){if(id==null||!dataScopeService.currentScope().allows(id))throw new BusinessException("无权访问该实例");var target=instanceService.getCollectTarget(id);if(target==null)throw new BusinessException("实例不存在");if(DbType.of(target.getDbType()) != DbType.POSTGRESQL)throw new BusinessException("该功能仅支持 PostgreSQL 实例");}
    private PgOperationalEventVo event(Map<String,Object> r){PgOperationalEventVo v=new PgOperationalEventVo();v.setId(longValue(r,"id"));v.setSource(text(r,"source"));v.setCategory(text(r,"category"));v.setEventType(text(r,"event_type"));v.setSeverity(text(r,"severity"));v.setDatabase(text(r,"database_name"));v.setUser(text(r,"user_name"));v.setObjectName(text(r,"object_name"));v.setQueryId(text(r,"query_id"));v.setSqlState(text(r,"sql_state"));v.setMessage(text(r,"message"));v.setFingerprint(text(r,"fingerprint"));v.setSensitiveRedacted(Boolean.TRUE.equals(r.get("sensitive_redacted")));v.setPayload(json(r.get("payload")));v.setEventTime(time(r.get("event_time")));v.setCollectedAt(time(r.get("collected_at")));return v;}
    private PgOperationalSummaryVo summaryVo(Map<String,Object> r){
        PgOperationalSummaryVo v=new PgOperationalSummaryVo();
        v.setCategory(text(r,"category"));v.setEventType(text(r,"event_type"));v.setSeverity(text(r,"severity"));
        v.setEventCount(longValue(r,"event_count"));v.setFingerprintCount(longValue(r,"fingerprint_count"));v.setLastSeen(time(r.get("last_seen")));
        PgOperationalGuidance guidance=PgOperationalGuidance.resolve(v.getCategory(),v.getEventType(),v.getSeverity());
        v.setConclusion(guidance.conclusion());v.setPossibleCause(guidance.possibleCause());v.setImpact(guidance.impact());v.setAction(guidance.action());
        return v;
    }
    private static int severityRank(String severity){return "critical".equals(severity)?2:"warning".equals(severity)?1:0;}
    private PgRestoreDrillVo drill(Map<String,Object> r){PgRestoreDrillVo v=new PgRestoreDrillVo();v.setId(longValue(r,"id"));v.setBackupId(text(r,"backup_id"));v.setTargetTime(time(r.get("target_time")));v.setStartedAt(time(r.get("started_at")));v.setFinishedAt(time(r.get("finished_at")));v.setStatus(text(r,"status"));v.setValidationResult(text(r,"validation_result"));v.setDurationSeconds(nullableLong(r,"duration_seconds"));v.setOwnerName(text(r,"owner_name"));v.setNotes(text(r,"notes"));v.setCreatedAt(time(r.get("created_at")));return v;}
    @SuppressWarnings("unchecked") private static Map<String,Object> json(Object value){if(value instanceof Map<?,?> m){Map<String,Object> out=new LinkedHashMap<>();m.forEach((k,v)->out.put(String.valueOf(k),v));return out;}try{return JSONUtil.parseObj(String.valueOf(value)).toBean(Map.class);}catch(Exception e){return Map.of();}}
    private static Timestamp ts(OffsetDateTime t){return Timestamp.from(t.toInstant());}private static Timestamp nullableTs(OffsetDateTime t){return t==null?null:ts(t);}private static OffsetDateTime time(Object o){if(o instanceof OffsetDateTime t)return t;if(o instanceof Timestamp t)return t.toInstant().atOffset(java.time.ZoneOffset.UTC);return null;}
    private static String text(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:String.valueOf(v);}private static long longValue(Map<String,Object> r,String k){Object v=r.get(k);return v instanceof Number n?n.longValue():v==null?0:Long.parseLong(v.toString());}private static Long nullableLong(Map<String,Object> r,String k){return r.get(k)==null?null:longValue(r,k);}private static String trim(String v){return StringUtils.hasText(v)?v.trim():null;}private static String truncate(String v,int max){return v!=null&&v.length()>max?v.substring(0,max):v;}private static String redact(String v){return v==null?null:SECRET.matcher(v).replaceAll("$1=<REDACTED>");}
}