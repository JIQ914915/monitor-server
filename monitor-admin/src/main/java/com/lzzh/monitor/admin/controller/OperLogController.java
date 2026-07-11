package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.OperLogPageRequest;
import com.lzzh.monitor.api.response.OperLogVo;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.log.OperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 操作日志（审计只读 + 导出）。 */
@Tag(name = "操作日志", description = "操作审计日志的分页查询与导出")
@RestController
@RequestMapping("/api/v1/oper-logs")
public class OperLogController {

    private final OperLogService operLogService;

    public OperLogController(OperLogService operLogService) {
        this.operLogService = operLogService;
    }

    /**
     * 分页查询操作日志。
     *
     * @param req 分页与过滤条件（用户名/动作/模块/时间范围），可为空
     * @return 操作日志分页结果
     */
    @Operation(summary = "分页查询操作日志", description = "按用户名、动作、模块、时间范围分页检索操作日志")
    @PostMapping("/page")
    public Result<PageResult<OperLogVo>> page(@Valid @RequestBody(required = false) OperLogPageRequest req) {
        return Result.ok(operLogService.page(req == null ? new OperLogPageRequest() : req));
    }

    /**
     * 导出操作日志。
     *
     * @param req 分页与过滤条件（用户名/动作/模块/时间范围），可为空
     * @return 符合条件的操作日志列表（不分页）
     */
    @Operation(summary = "导出操作日志", description = "按过滤条件返回全部匹配的操作日志，用于导出")
    @PostMapping("/export")
    public Result<List<OperLogVo>> export(@Valid @RequestBody(required = false) OperLogPageRequest req) {
        return Result.ok(operLogService.listForExport(req == null ? new OperLogPageRequest() : req));
    }
}
