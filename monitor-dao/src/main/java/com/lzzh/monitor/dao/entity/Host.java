package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 数据库主机登记（主机指标采集配置，V133）。
 * <p>一台主机可承载多个数据库实例（db_instance.host_id 关联）；
 * 主机指标按「主机采集一次 → 关联实例扇出写入」进入 metric_data_1m。
 */
@Data
@TableName("host")
public class Host {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主机业务编码（稳定唯一，采集分片依据）。 */
    private String hostCode;

    private String name;

    private String ip;

    /** 操作系统类型（一期仅 linux）。 */
    private String osType;

    /** 采集方式：exporter / ssh（二期）/ none（字典 host_collect_mode）。 */
    private String collectMode;

    private Integer exporterPort;

    private String exporterPath;

    /** SSH 免 Agent 预留字段（二期）。 */
    private Integer sshPort;
    private String sshUser;
    /** AES 加密存储。 */
    private String sshPassword;

    private String remark;

    /** 状态：normal / abnormal / paused（字典 host_status）。 */
    private String status;

    @TableField("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @TableField("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
