package com.lzzh.monitor.service.sqlserver;

import com.lzzh.monitor.common.enums.DbType;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.SqlServerRestoreDrillPageRequest;
import com.lzzh.monitor.api.request.SqlServerRestoreDrillRequest;
import com.lzzh.monitor.api.response.SqlServerRestoreDrillVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.SysDictItem;
import com.lzzh.monitor.dao.mapper.SqlServerOperationsMapper;
import com.lzzh.monitor.dao.mapper.SysDictItemMapper;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceService;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SqlServerOperationsServiceImpl implements SqlServerOperationsService {
 private static final Pattern SECRET=Pattern.compile("(?i)(password|token|api[_-]?key|authorization)\\s*[:=]\\s*\\S+");
 @Resource private SqlServerOperationsMapper mapper;@Resource private SysDictItemMapper dictMapper;
 @Resource private InstanceService instanceService;@Resource private DataScopeService dataScopeService;
 @Override public PageResult<SqlServerRestoreDrillVo> restoreDrills(SqlServerRestoreDrillPageRequest request){
  requireInstance(request.getInstanceId());Pages.PageWindow p=Pages.window(request);long total=mapper.countRestoreDrills(request.getInstanceId());
  return PageResult.of(total==0?List.of():mapper.selectRestoreDrills(request.getInstanceId(),p.pageSize(),p.offset()).stream().map(this::vo).toList(),total);
 }
 @Override public SqlServerRestoreDrillVo saveRestoreDrill(SqlServerRestoreDrillRequest request){
  requireInstance(request.getInstanceId());requireDict("sqlserver_restore_drill_status",request.getStatus());requireDict("sqlserver_restore_validation",request.getValidationResult());
  if(request.getFinishedAt()!=null&&request.getFinishedAt().isBefore(request.getStartedAt()))throw new BusinessException("结束时间不能早于开始时间");
  Map<String,Object> r=new LinkedHashMap<>();r.put("instanceId",request.getInstanceId());r.put("backupReference",limit(request.getBackupReference(),256));
  r.put("startedAt",ts(request.getStartedAt()));r.put("finishedAt",request.getFinishedAt()==null?null:ts(request.getFinishedAt()));r.put("status",request.getStatus());
  r.put("validationResult",request.getValidationResult());r.put("rtoSeconds",request.getFinishedAt()==null?null:Duration.between(request.getStartedAt(),request.getFinishedAt()).toSeconds());
  r.put("ownerName",limit(request.getOwnerName(),128));r.put("notes",redact(limit(request.getNotes(),4000)));r.put("createdBy",null);mapper.insertRestoreDrill(r);
  Long id=r.get("id") instanceof Number n?n.longValue():null;return mapper.selectRestoreDrills(request.getInstanceId(),200,0).stream().filter(x->id==null||number(x.get("id"))==id).map(this::vo).findFirst().orElseThrow(()->new BusinessException("恢复演练保存失败"));
 }
 private void requireInstance(Long id){if(id==null||!dataScopeService.currentScope().allows(id))throw new BusinessException("无权访问该实例");var t=instanceService.getCollectTarget(id);if(t==null)throw new BusinessException("实例不存在");if(DbType.of(t.getDbType()) != DbType.SQLSERVER)throw new BusinessException("该功能仅支持 SQL Server 实例");}
 private void requireDict(String type,String value){if(value==null||dictMapper.selectCount(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getDictType,type).eq(SysDictItem::getItemValue,value).eq(SysDictItem::getStatus,"enabled"))==0)throw new BusinessException("状态字典值不合法或已停用");}
 private SqlServerRestoreDrillVo vo(Map<String,Object>r){SqlServerRestoreDrillVo v=new SqlServerRestoreDrillVo();v.setId(number(r.get("id")));v.setBackupReference(text(r,"backup_reference"));v.setStartedAt(time(r.get("started_at")));v.setFinishedAt(time(r.get("finished_at")));v.setStatus(text(r,"status"));v.setValidationResult(text(r,"validation_result"));v.setRtoSeconds(r.get("rto_seconds")==null?null:number(r.get("rto_seconds")));v.setOwnerName(text(r,"owner_name"));v.setNotes(text(r,"notes"));v.setCreatedAt(time(r.get("created_at")));return v;}
 private static Timestamp ts(OffsetDateTime t){return Timestamp.from(t.toInstant());}private static OffsetDateTime time(Object o){if(o instanceof OffsetDateTime t)return t;if(o instanceof Timestamp t)return t.toInstant().atOffset(java.time.ZoneOffset.UTC);return null;}
 private static long number(Object o){return o instanceof Number n?n.longValue():Long.parseLong(String.valueOf(o));}private static String text(Map<String,Object>r,String k){return r.get(k)==null?null:String.valueOf(r.get(k));}
 private static String limit(String v,int n){return v!=null&&v.length()>n?v.substring(0,n):v;}private static String redact(String v){return v==null?null:SECRET.matcher(v).replaceAll("$1=<REDACTED>");}
}
