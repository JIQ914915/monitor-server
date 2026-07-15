package com.lzzh.monitor.api.response;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class PgPlanHistoryVo {
    private Long id;
    private String database;
    private String queryId;
    private String sqlHash;
    private String planHash;
    private String previousPlanHash;
    private boolean planChanged;
    private Object plan;
    private List<Map<String, Object>> nodeSummary;
    private OffsetDateTime capturedAt;
}