package com.lzzh.monitor.api.response;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class PgOperationalHealthVo {
    private String severity;
    private String conclusion;
    private long riskCount;
    private long affectedObjectCount;
    private OffsetDateTime lastSeen;
    private List<PgOperationalSummaryVo> risks;
}
