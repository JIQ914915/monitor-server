package com.lzzh.monitor.api.response;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class PgOperationalSummaryVo {
    private String category;
    private String eventType;
    private String severity;
    private long eventCount;
    private long fingerprintCount;
    private OffsetDateTime lastSeen;
    private String conclusion;
    private String possibleCause;
    private String impact;
    private String action;
}
