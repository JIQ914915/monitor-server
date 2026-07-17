package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.DatabaseVersionListRequest;
import com.lzzh.monitor.api.request.DatabaseVersionRequest;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.response.DatabaseVersionVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.datatype.DatabaseVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 数据库版本管理（§5.8）：系统设置维护 CRUD。 */
@Tag(name = "数据库版本管理", description = "数据库版本配置的维护（系统设置）")
@RestController
@RequestMapping("/api/v1/database-versions")
public class DatabaseVersionController {

    @Resource
    private DatabaseVersionService versionService;

    /**
     * 查询数据库版本列表（可按 dbType 过滤）。
     *
     * @param req 查询条件（可空，留空返回全部）
     * @return 版本列表
     */
    @Operation(summary = "数据库版本列表", description = "返回全部版本配置（可按 dbType 过滤），供系统设置维护")
    @PostMapping("/list")
    @RequiresPerm("db_version:list")
    public Result<List<DatabaseVersionVo>> list(@RequestBody(required = false) DatabaseVersionListRequest req) {
        return Result.ok(versionService.list(req == null ? null : req.getDbType()));
    }

    /**
     * 新增数据库版本。
     *
     * @param req 版本信息
     * @return 新建记录 ID
     */
    @Operation(summary = "新增数据库版本")
    @PostMapping
    @RequiresPerm("db_version:create")
    @OperateLog(module = "数据库版本", action = "新增")
    public Result<Long> create(@Valid @RequestBody DatabaseVersionRequest req) {
        return Result.ok(versionService.create(req));
    }

    /**
     * 修改数据库版本。
     *
     * @param req 版本信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改数据库版本")
    @PostMapping("/update")
    @RequiresPerm("db_version:update")
    @OperateLog(module = "数据库版本", action = "修改")
    public Result<Void> update(@Valid @RequestBody DatabaseVersionRequest req) {
        versionService.update(req);
        return Result.ok();
    }

    /**
     * 删除数据库版本。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除数据库版本")
    @PostMapping("/delete")
    @RequiresPerm("db_version:delete")
    @OperateLog(module = "数据库版本", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        versionService.delete(req.getId());
        return Result.ok();
    }
}
