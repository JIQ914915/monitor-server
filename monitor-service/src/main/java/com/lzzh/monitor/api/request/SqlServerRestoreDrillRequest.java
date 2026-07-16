package com.lzzh.monitor.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
public class SqlServerRestoreDrillRequest {
 @NotNull private Long instanceId; private String backupReference;
 @NotNull private OffsetDateTime startedAt; private OffsetDateTime finishedAt;
 @NotBlank private String status; @NotBlank private String validationResult;
 @NotBlank private String ownerName; private String notes;
}
