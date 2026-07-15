package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 实例（响应，不含采集密码）。 */
@Data
@Schema(description = "实例信息（响应，不含采集密码）")
public class InstanceVo {

    @Schema(description = "主键 ID", example = "1")
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

    @Schema(description = "数据库类型展示名（由 dbTypeId 解析）", example = "MySQL")
    private String dbType;

    @Schema(description = "数据库版本编码（由 dbVersionId 解析）", example = "8.0")
    private String dbVersion;

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

    @Schema(description = "连接目标数据库名（采集建连时替换 URL 模板中的 {database}）", example = "mydb")
    private String databaseName;

    @Schema(description = "PG对象级采集范围：monitoring / selected / all")
    private String pgObjectScope;

    @Schema(description = "PG对象级采集选定数据库列表")
    private List<String> pgObjectDatabases;

    @Schema(description = "所在主机 ID（host.id，可空）", example = "1")
    private Long hostId;

    @Schema(description = "所在主机名称（由 hostId 解析）", example = "生产库主机-01")
    private String hostName;

    @Schema(description = "所在主机操作系统类型（由 hostId 解析）：linux / windows", example = "linux")
    private String hostOsType;

    @Schema(description = "健康度（0-100）", example = "92")
    private Integer health;

    @Schema(description = "状态：normal（正常）/ abnormal（异常）/ paused（暂停采集）", example = "normal")
    private String status;

    @Schema(description = "创建时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @Schema(description = "更新时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
