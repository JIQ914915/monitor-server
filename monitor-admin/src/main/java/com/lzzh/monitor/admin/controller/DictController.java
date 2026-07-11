package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.DictItemQuery;
import com.lzzh.monitor.api.request.DictItemRequest;
import com.lzzh.monitor.api.request.DictTypeRequest;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.response.DictItemVo;
import com.lzzh.monitor.api.response.DictTypeVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.dict.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 数据字典管理（字典类型 + 字典项）。 */
@Tag(name = "字典管理", description = "数据字典的字典类型与字典项维护，供前端下拉/标签渲染")
@RestController
@RequestMapping("/api/v1/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    // ===== 字典类型 =====

    /**
     * 查询全部字典类型。
     *
     * @return 字典类型列表（左侧导航用）
     */
    @Operation(summary = "查询全部字典类型", description = "返回全部字典类型，供左侧导航展示")
    @GetMapping("/types/list")
    public Result<List<DictTypeVo>> listTypes() {
        return Result.ok(dictService.listTypes());
    }

    /**
     * 新增字典类型。
     *
     * @param req 字典类型信息
     * @return 新建字典类型的主键 ID
     */
    @Operation(summary = "新增字典类型", description = "创建一个新的字典类型，编码需全局唯一")
    @PostMapping("/types")
    @RequiresPerm("dict:create")
    @OperateLog(module = "字典管理", action = "新增类型")
    public Result<Long> createType(@Valid @RequestBody DictTypeRequest req) {
        return Result.ok(dictService.createType(req));
    }

    /**
     * 修改字典类型。
     *
     * @param req 字典类型信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改字典类型", description = "按主键更新字典类型；编码变更时同步其下字典项")
    @PostMapping("/types/update")
    @RequiresPerm("dict:update")
    @OperateLog(module = "字典管理", action = "修改类型")
    public Result<Void> updateType(@Valid @RequestBody DictTypeRequest req) {
        dictService.updateType(req);
        return Result.ok();
    }

    /**
     * 删除字典类型。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除字典类型", description = "按主键删除字典类型，并连同其下所有字典项")
    @PostMapping("/types/delete")
    @RequiresPerm("dict:delete")
    @OperateLog(module = "字典管理", action = "删除类型")
    public Result<Void> deleteType(@Valid @RequestBody IdRequest req) {
        dictService.deleteType(req.getId());
        return Result.ok();
    }

    // ===== 字典项 =====

    /**
     * 按字典类型查询字典项。
     *
     * @param req 字典类型查询入参
     * @return 该字典类型下的字典项列表
     */
    @Operation(summary = "查询字典项", description = "按字典类型编码查询其下字典项（排序升序）")
    @PostMapping("/items/list")
    public Result<List<DictItemVo>> listItems(@Valid @RequestBody DictItemQuery req) {
        return Result.ok(dictService.listItems(req.getDictType()));
    }

    /**
     * 新增字典项。
     *
     * @param req 字典项信息
     * @return 新建字典项的主键 ID
     */
    @Operation(summary = "新增字典项", description = "在指定字典类型下创建字典项，字典值同类型下唯一")
    @PostMapping("/items")
    @RequiresPerm("dict:create")
    @OperateLog(module = "字典管理", action = "新增字典项")
    public Result<Long> createItem(@Valid @RequestBody DictItemRequest req) {
        return Result.ok(dictService.createItem(req));
    }

    /**
     * 修改字典项。
     *
     * @param req 字典项信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改字典项", description = "按主键更新字典项信息")
    @PostMapping("/items/update")
    @RequiresPerm("dict:update")
    @OperateLog(module = "字典管理", action = "修改字典项")
    public Result<Void> updateItem(@Valid @RequestBody DictItemRequest req) {
        dictService.updateItem(req);
        return Result.ok();
    }

    /**
     * 删除字典项。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除字典项", description = "按主键删除字典项")
    @PostMapping("/items/delete")
    @RequiresPerm("dict:delete")
    @OperateLog(module = "字典管理", action = "删除字典项")
    public Result<Void> deleteItem(@Valid @RequestBody IdRequest req) {
        dictService.deleteItem(req.getId());
        return Result.ok();
    }
}
