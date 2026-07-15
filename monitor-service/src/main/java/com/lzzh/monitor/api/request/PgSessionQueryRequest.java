package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "PostgreSQL 实时会话查询")
public class PgSessionQueryRequest {
    @NotNull private Long instanceId;
    private String database;
    private String user;
    private String application;
    private String state;
    private String waitEventType;
    private Long minDurationSeconds;
}