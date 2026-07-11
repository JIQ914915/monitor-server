package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 实例连接测试结果（差距分析 模块9：采集账号权限逐项检测）。
 * 连接成功后逐项核实采集账号权限，缺失项给出受影响的监控能力，
 * 引导用户在创建实例时就把账号权限补齐，避免上线后"部分图表无数据"。
 */
@Data
@Schema(description = "实例连接测试结果（版本 + 采集账号权限逐项检测）")
public class ConnectionTestVo {

    @Schema(description = "数据库版本号", example = "8.0.32")
    private String version;

    @Schema(description = "权限检测项列表（MySQL 实例才有，其他类型为空）")
    private List<PermissionCheck> checks;

    @Data
    @Schema(description = "单项权限检测结果")
    public static class PermissionCheck {

        @Schema(description = "权限/能力名称", example = "PROCESS 权限")
        private String name;

        @Schema(description = "是否具备（null=无法确认）")
        private Boolean granted;

        @Schema(description = "受影响的监控能力（缺失时展示）", example = "连接分析、锁等待、活动会话")
        private String affected;

        @Schema(description = "补齐建议（缺失时展示）", example = "GRANT PROCESS ON *.* TO 'monitor'@'%';")
        private String grantHint;

        public static PermissionCheck of(String name, Boolean granted, String affected, String grantHint) {
            PermissionCheck c = new PermissionCheck();
            c.setName(name);
            c.setGranted(granted);
            c.setAffected(affected);
            c.setGrantHint(grantHint);
            return c;
        }
    }
}
