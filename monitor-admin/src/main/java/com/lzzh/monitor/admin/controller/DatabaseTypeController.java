package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.DatabaseTypeRequest;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.response.DatabaseTypeVo;
import com.lzzh.monitor.api.response.DbTypeOptionVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.datatype.DatabaseTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 数据库类型管理（§5.8）：下拉选项（实例表单）+ 管理 CRUD（系统设置）。 */
@Tag(name = "数据库类型管理", description = "数据库类型元数据维护：供实例表单下拉 + 系统设置页面 CRUD")
@RestController
@RequestMapping("/api/v1/database-types")
public class DatabaseTypeController {

    @Resource
    private DatabaseTypeService databaseTypeService;

    // ===== 实例表单下拉（原有接口保持不变） =====

    /**
     * 启用的数据库类型选项（含默认端口与版本列表），一次性返回，前端据此构建类型/版本下拉。
     *
     * @return 数据库类型选项列表
     */
    @Operation(summary = "数据库类型选项（下拉）", description = "返回启用的数据库类型及其默认端口、支持版本列表")
    @PostMapping("/options")
    public Result<List<DbTypeOptionVo>> listOptions() {
        return Result.ok(databaseTypeService.listTypeOptions());
    }

    // ===== 系统设置维护 CRUD =====

    /**
     * 查询全部数据库类型（管理视图，含禁用）。
     *
     * @return 数据库类型管理视图列表
     */
    @Operation(summary = "数据库类型列表（管理）", description = "返回全部数据库类型（含禁用），供系统设置维护")
    @PostMapping("/admin/list")
    @RequiresPerm("db_type:list")
    public Result<List<DatabaseTypeVo>> listAll() {
        return Result.ok(databaseTypeService.listAll());
    }

    /**
     * 新增数据库类型。
     *
     * @param req 数据库类型信息
     * @return 新建记录 ID
     */
    @Operation(summary = "新增数据库类型")
    @PostMapping("/admin")
    @RequiresPerm("db_type:create")
    @OperateLog(module = "数据库类型", action = "新增")
    public Result<Long> create(@Valid @RequestBody DatabaseTypeRequest req) {
        return Result.ok(databaseTypeService.create(req));
    }

    /**
     * 修改数据库类型。
     *
     * @param req 数据库类型信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改数据库类型")
    @PostMapping("/admin/update")
    @RequiresPerm("db_type:update")
    @OperateLog(module = "数据库类型", action = "修改")
    public Result<Void> update(@Valid @RequestBody DatabaseTypeRequest req) {
        databaseTypeService.update(req);
        return Result.ok();
    }

    /**
     * 删除数据库类型。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除数据库类型")
    @PostMapping("/admin/delete")
    @RequiresPerm("db_type:delete")
    @OperateLog(module = "数据库类型", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        databaseTypeService.delete(req.getId());
        return Result.ok();
    }
}
