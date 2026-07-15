package com.lzzh.monitor.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PgPlanCaptureRequest {
    @NotNull private Long instanceId;
    @NotBlank private String database;
    @NotBlank private String queryId;
    @NotBlank private String sql;
}