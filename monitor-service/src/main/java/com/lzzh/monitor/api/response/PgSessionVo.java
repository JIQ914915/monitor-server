package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PgSessionVo {
    private int pid;
    private String database;
    private String user;
    private String application;
    private String clientAddress;
    private String backendType;
    private String state;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime transactionStart;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime queryStart;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime stateChange;
    private long durationSeconds;
    private String waitEventType;
    private String waitEvent;
    private String queryId;
    private String query;
    private List<Integer> blockedBy = new ArrayList<>();
    private List<String> lockedObjects = new ArrayList<>();
    private boolean rootBlocker;
}