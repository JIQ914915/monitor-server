package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.SqlServerRestoreDrillPageRequest;
import com.lzzh.monitor.api.request.SqlServerRestoreDrillRequest;
import com.lzzh.monitor.api.response.SqlServerRestoreDrillVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.sqlserver.SqlServerOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name="SQL Server 运维保障")
@RestController @RequestMapping("/api/v1/sqlserver")
public class SqlServerController {
 @Resource private SqlServerOperationsService service;
 @Operation(summary="恢复演练记录") @PostMapping("/restore-drills") @RequiresPerm("sqlserver_backup:view")
 public Result<PageResult<SqlServerRestoreDrillVo>> restoreDrills(@Valid @RequestBody SqlServerRestoreDrillPageRequest r){return Result.ok(service.restoreDrills(r));}
 @Operation(summary="登记外部人工恢复演练") @PostMapping("/restore-drills/save") @RequiresPerm("sqlserver_restore_drill:manage")
 @OperateLog(module="SQL Server 备份恢复",action="登记恢复演练")
 public Result<SqlServerRestoreDrillVo> save(@Valid @RequestBody SqlServerRestoreDrillRequest r){return Result.ok(service.saveRestoreDrill(r));}
}
