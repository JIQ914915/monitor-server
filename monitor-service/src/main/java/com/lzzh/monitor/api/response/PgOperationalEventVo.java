package com.lzzh.monitor.api.response;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class PgOperationalEventVo {
    private Long id; private String source; private String category; private String eventType;
    private String severity; private String database; private String user; private String objectName;
    private String queryId; private String sqlState; private String message; private String fingerprint;
    private Map<String,Object> payload; private boolean sensitiveRedacted;
    private OffsetDateTime eventTime; private OffsetDateTime collectedAt;
}