package com.lzzh.monitor.api.response;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
public class SqlServerRestoreDrillVo {
 private Long id;private String backupReference;private OffsetDateTime startedAt;private OffsetDateTime finishedAt;
 private String status;private String validationResult;private Long rtoSeconds;private String ownerName;private String notes;private OffsetDateTime createdAt;
}
