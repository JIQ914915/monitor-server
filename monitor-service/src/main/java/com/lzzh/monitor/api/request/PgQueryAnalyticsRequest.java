package com.lzzh.monitor.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PgQueryAnalyticsRequest {
    @NotNull private Long instanceId;
    private String database;
    private String user;
    private String queryId;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String sortBy;
    private String sortDirection;
    private Integer limit;
}