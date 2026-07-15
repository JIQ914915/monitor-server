package com.lzzh.monitor.api.response;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class PgSqlRegressionVo {
    private Long id;
    private String database;
    private String queryId;
    private String queryText;
    private String type;
    private String severity;
    private OffsetDateTime baselineFrom;
    private OffsetDateTime baselineTo;
    private OffsetDateTime currentFrom;
    private OffsetDateTime currentTo;
    private Double baselineValue;
    private Double currentValue;
    private Double changeRatio;
    private Map<String, Object> evidence;
    private OffsetDateTime detectedAt;
}