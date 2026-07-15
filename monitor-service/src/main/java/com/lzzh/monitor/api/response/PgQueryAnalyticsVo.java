package com.lzzh.monitor.api.response;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PgQueryAnalyticsVo {
    private String database;
    private String user;
    private String queryId;
    private String queryText;
    private long calls;
    private double totalExecTimeMs;
    private double avgExecTimeMs;
    private double minExecTimeMs;
    private double maxExecTimeMs;
    private double stddevExecTimeMs;
    private double planCount;
    private double totalPlanTimeMs;
    private double avgPlanTimeMs;
    private double sharedHit;
    private double sharedRead;
    private double sharedDirtied;
    private double sharedWritten;
    private double localHit;
    private double localRead;
    private double tempRead;
    private double tempWritten;
    private Double blockReadTimeMs;
    private Double blockWriteTimeMs;
    private double walRecords;
    private double walFpi;
    private double walBytes;
    private double jitFunctions;
    private double jitTimeMs;
    private long rows;
    private OffsetDateTime firstSeen;
    private OffsetDateTime lastSeen;
    private OffsetDateTime statsReset;
    private long deallocations;
}