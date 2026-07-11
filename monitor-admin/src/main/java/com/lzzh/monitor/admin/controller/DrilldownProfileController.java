package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.DrilldownProfileSaveRequest;
import com.lzzh.monitor.api.request.EnabledRequest;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.response.DrilldownProfileVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.alert.AlertDrilldownProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 告警下钻画像管理（§11.7 事件下钻）：画像的查询、保存、删除与启停。 */
@Tag(name = "下钻画像", description = "告警下钻画像的查询、保存、删除与启停（按触发指标编码匹配，驱动下钻页四块内容）")
@RestController
@RequestMapping("/api/v1/alerts/drilldown-profiles")
public class DrilldownProfileController {

    private final AlertDrilldownProfileService profileService;

    public DrilldownProfileController(AlertDrilldownProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * 查询全部画像。
     *
     * @return 画像列表（按排序号升序）
     */
    @Operation(summary = "查询全部画像", description = "画像数量少（≤20），一次性返回全部，前端本地筛选")
    @PostMapping("/list")
    @RequiresPerm("drilldown_profile:view")
    public Result<List<DrilldownProfileVo>> list() {
        return Result.ok(profileService.list());
    }

    /**
     * 新增或更新画像。
     *
     * @param req 画像信息（id 为空新增）
     * @return 画像主键 ID
     */
    @Operation(summary = "保存画像", description = "id 为空新增，否则更新；内置画像不允许修改编码")
    @PostMapping("/save")
    @RequiresPerm("drilldown_profile:update")
    @OperateLog(module = "下钻画像", action = "保存")
    public Result<Long> save(@Valid @RequestBody DrilldownProfileSaveRequest req) {
        return Result.ok(profileService.save(req));
    }

    /**
     * 删除画像。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除画像", description = "内置画像不可删除")
    @PostMapping("/delete")
    @RequiresPerm("drilldown_profile:delete")
    @OperateLog(module = "下钻画像", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        profileService.delete(req.getId());
        return Result.ok();
    }

    /**
     * 启停画像。
     *
     * @param req 主键与启停状态
     * @return 空响应体
     */
    @Operation(summary = "启停画像", description = "停用后匹配时跳过该画像")
    @PostMapping("/toggle")
    @RequiresPerm("drilldown_profile:toggle")
    @OperateLog(module = "下钻画像", action = "启停")
    public Result<Void> toggle(@Valid @RequestBody EnabledRequest req) {
        profileService.toggle(req.getId(), Boolean.TRUE.equals(req.getEnabled()));
        return Result.ok();
    }
}
