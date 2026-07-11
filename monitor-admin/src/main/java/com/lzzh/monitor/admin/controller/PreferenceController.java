package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.SecurityUtils;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.preference.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 当前用户个性化偏好（账号级主题持久化，方案 §8.5）。 */
@Tag(name = "个性化偏好", description = "当前用户账号级主题偏好的获取与保存")
@RestController
@RequestMapping("/api/v1/preference")
public class PreferenceController {

    @Resource
    private UserPreferenceService preferenceService;

    /**
     * 获取当前用户主题（无则返回 null，前端回落系统默认）。
     *
     * @return 当前用户主题配置键值对，无则为 null
     */
    @Operation(summary = "获取主题偏好", description = "返回当前用户账号级主题配置，无则返回 null 由前端回落系统默认")
    @GetMapping("/theme")
    public Result<Map<String, Object>> getTheme() {
        return Result.ok(preferenceService.getTheme(SecurityUtils.current().id()));
    }

    /**
     * 保存当前用户主题。
     *
     * @param theme 主题配置键值对
     * @return 空响应体
     */
    @Operation(summary = "保存主题偏好", description = "持久化当前用户账号级主题配置")
    @PostMapping("/theme")
    public Result<Void> saveTheme(@RequestBody Map<String, Object> theme) {
        preferenceService.saveTheme(SecurityUtils.current().id(), theme);
        return Result.ok();
    }
}
