package com.lzzh.monitor.api.response;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class MySqlPlanHistoryVo {
    private Long id;
    private String schemaName;
    private String sqlHash;
    private String planHash;
    private String previousPlanHash;
    private boolean planChanged;
    private String planFormat;
    private Object plan;
    private List<Map<String,Object>> nodeSummary;
    private String riskLevel;
    private String conclusion;
    private String capturedBy;
    private OffsetDateTime capturedAt;
}
