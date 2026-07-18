package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 实例监控能力状态（需求 §20：能力矩阵 + 页面降级）。
 * status 取值由字典 capability_status 统一维护：
 * available / limited / permission_denied / version_not_support / edition_not_support / not_enabled / not_applicable / collect_error / no_data。
 */
@Data
@Schema(description = "实例监控能力状态（status 见字典 capability_status）")
public class InstanceCapabilityVo {

    @Schema(description = "能力键", example = "top_sql")
    private String capability;

    @Schema(description = "能力名称", example = "Top SQL 指纹分析")
    private String name;

    @Schema(description = "能力状态（字典 capability_status）", example = "version_not_support")
    private String status;

    @Schema(description = "面向用户的说明与引导（非 available 时展示）",
            example = "MySQL 5.6 不支持 performance_schema 语句摘要，已降级为慢日志样本采集")
    private String message;

    public static InstanceCapabilityVo of(String capability, String name, String status, String message) {
        InstanceCapabilityVo vo = new InstanceCapabilityVo();
        vo.setCapability(capability);
        vo.setName(name);
        vo.setStatus(status);
        vo.setMessage(message);
        return vo;
    }
}
