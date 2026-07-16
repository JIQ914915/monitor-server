package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 数据库实例（§6.1：多分组 groupIds + 负责人A/B）。只含持久列，展示用字段在 InstanceVo 中组装。 */
@Data
@TableName(value = "db_instance", autoResultMap = true)
public class DbInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实例业务编码（稳定唯一，分片/引用依据，§21.2.2）。 */
    private String instanceCode;

    private String name;
    private String host;
    private Integer port;

    /** 数据库类型ID（外键 database_type.id）。 */
    private Long dbTypeId;

    /** 数据库版本ID（外键 database_version.id）。字段严禁命名为 version。 */
    private Long dbVersionId;

    /** 连接目标数据库名（如 mydb），采集侧建连时替换 URL 模板中的 {database} 占位符。 */
    private String databaseName;

    /** PG对象级采集范围：monitoring / selected / all。 */
    private String pgObjectScope;

    /** PG对象级采集选定数据库列表。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> pgObjectDatabases;

    /** PG 三期外部只读数据源；路径必须由 collector 节点可读，URL 禁止内嵌凭据。 */

    /** 最近一次 PG 能力探测快照。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> pgCapabilities;

    /** 最近一次 PG 能力探测时间。 */
    private OffsetDateTime pgCapabilitiesDetectedAt;

    /** 最近一次 MySQL 能力探测快照，供巡检报告复用，避免报告批量实时探测目标库。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> mysqlCapabilities;

    private OffsetDateTime mysqlCapabilitiesDetectedAt;

    /** 所在主机ID（外键 host.id，可空）；关联后主机 host.* 指标扇出写入该实例。 */
    private Long hostId;

    /** 备注。 */
    private String remark;

    /** 所属分组（多分组），jsonb 存储。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> groupIds;

    /**
     * 连接来源白名单（IP 精确或 "10.0.1.*" 前缀通配），jsonb 存储。
     * 非空时采集侧对 processlist 来源做白名单比对，产出未知来源指标与告警；空 = 不启用。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> connSourceWhitelist;

    /** 负责人A用户ID（关联 sys_user.id，必填）。 */
    private Long ownerAId;

    /** 负责人B用户ID（关联 sys_user.id，可选）。 */
    private Long ownerBId;

    /** 采集账号（加密存储，返回时脱敏，§13.3.2）。 */
    private String connUser;
    private String connPassword;

    /** 最新健康分（0-100）。 */
    private Integer health;

    /**
     * 五维健康得分快照（availability/performance/stability/capacity/security，0-100，-1=无数据），
     * 健康评分作业随总分一并写入，供首页整体健康总览聚合五维达标率。jsonb 存储。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> healthDims;

    /** 状态：normal / abnormal / paused。 */
    private String status;

    @TableField("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @TableField("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
