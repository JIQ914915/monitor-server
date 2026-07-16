package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class PgQueryAnalyticsRequest extends PageParam {
    @NotNull private Long instanceId;
    private String database;
    private String user;
    private String queryId;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String sortBy;
    private String sortDirection;
}
