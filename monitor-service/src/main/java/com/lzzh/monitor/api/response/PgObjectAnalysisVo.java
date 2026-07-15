package com.lzzh.monitor.api.response;

import lombok.Data;

@Data
public class PgObjectAnalysisVo {
    private String database;
    private String schema;
    private String objectType;
    private String objectName;
    private String parentName;
    private String tablespace;
    private long sizeBytes;
    private Long estimatedRows;
    private Long sequentialScans;
    private Double cacheHitRate;
}