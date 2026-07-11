package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 实例新增/编辑（请求）。 */
@Data
@Schema(description = "实例新增/编辑入参")
public class InstanceRequest {

    @Schema(description = "主键 ID，新增时为空，编辑时必填", example = "1")
    private Long id;

    @Schema(description = "实例名称", example = "生产库-订单")
    private String name;

    @Schema(description = "主机地址", example = "127.0.0.1")
    private String host;

    @Schema(description = "端口", example = "3306")
    private Integer port;

    @Schema(description = "数据库类型ID（database_type.id）", example = "1")
    private Long dbTypeId;

    @Schema(description = "数据库版本ID（database_version.id）", example = "3")
    private Long dbVersionId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "所属分组 ID 列表")
    private List<Long> groupIds;

    @Schema(description = "连接来源白名单（IP 精确或 \"10.0.1.*\" 前缀通配；空 = 不启用来源检测）")
    private List<String> connSourceWhitelist;

    @Schema(description = "主负责人用户 ID", example = "10")
    private Long ownerAId;

    @Schema(description = "备份负责人用户 ID", example = "11")
    private Long ownerBId;

    @Schema(description = "采集账号", example = "monitor")
    private String connUser;

    @Schema(description = "采集密码：编辑留空表示不修改")
    private String connPassword;

    @Schema(description = "连接目标数据库名（采集建连时替换 URL 模板中的 {database}）", example = "mydb")
    private String databaseName;

    @Schema(description = "所在主机 ID（host.id，可空）", example = "1")
    private Long hostId;

    @Schema(description = "健康度", example = "100")
    private Integer health;

    @Schema(description = "状态：normal / abnormal / paused", example = "normal")
    private String status;
}
