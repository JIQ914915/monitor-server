package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "PostgreSQL 会话处置请求")
public class PgSessionActionRequest {
    @NotNull private Long instanceId;
    @NotNull private Integer pid;
    @NotBlank private String reason;
}