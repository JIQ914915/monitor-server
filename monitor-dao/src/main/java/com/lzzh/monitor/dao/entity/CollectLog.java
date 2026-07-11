package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 采集运行日志（每次采集写一条，append-only）。
 *
 * <p>对应 collect_log 超表，主键为复合键 (id, collect_time)——TimescaleDB 超表要求分区列在主键内。
 * MyBatis-Plus 以 id 作为逻辑主键（AUTO 自增由 DB 生成），insert 时不需要手动赋值 id，
 * collect_time 由应用层赋值（不依赖 DEFAULT now() 以确保时区一致）。
 */
@Data
@TableName("collect_log")
public class CollectLog {

    /**
     * 自增主键（GENERATED ALWAYS AS IDENTITY）。
     * 由于超表复合主键 (id, collect_time)，MyBatis-Plus 仍能通过 AUTO 策略让 DB 自动生成 id；
     * insert 后 id 会被回填（单条 mapper.insert 可直接拿到数据库生成的主键）。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    /** 主机采集日志时填 host.id（此时 instanceId 填 0）；实例采集日志为 NULL。 */
    private Long hostId;

    /** 采集频率：1m / 1h / 1d。 */
    private String frequency;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime collectTime;

    /** 耗时（毫秒）。 */
    private Integer durationMs;

    /** 数值指标点数。 */
    private Integer metricCount;

    /** 文本指标点数。 */
    private Integer textCount;

    /** 对象级指标点数。 */
    private Integer objectCount;

    private Boolean success;

    private String errorMessage;
}
