package com.lzzh.monitor.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class PgRestoreDrillRequest {
    @NotNull private Long instanceId;
    private String backupId;
    private OffsetDateTime targetTime;
    @NotNull private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    @NotBlank private String status;
    @NotBlank private String validationResult;
    @NotBlank private String ownerName;
    private String notes;
}