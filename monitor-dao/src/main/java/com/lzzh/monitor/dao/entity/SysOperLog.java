package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志（@OperateLog AOP 落库；审计只读）。 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime operTime;
    private String username;
    private String module;
    private String action;
    private String target;
    private String ip;
    private Boolean success;
    private String detail;
}
