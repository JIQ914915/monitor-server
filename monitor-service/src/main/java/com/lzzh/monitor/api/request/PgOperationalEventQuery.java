package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class PgOperationalEventQuery extends PageParam {
    @NotNull private Long instanceId;
    private String source;
    private String category;
    private String sqlState;
    private String database;
    private String user;
    private OffsetDateTime from;
    private OffsetDateTime to;
}
