package com.lzzh.monitor.api.response;

import lombok.Data;
import java.time.OffsetDateTime;
@Data
public class PgRestoreDrillVo {
    private Long id; private String backupId; private OffsetDateTime targetTime; private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt; private String status; private String validationResult;
    private Long durationSeconds; private String ownerName; private String notes; private OffsetDateTime createdAt;
}