package com.lzzh.monitor.api.response;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class PgBlockingNodeVo {
    private int pid;
    private String database;
    private String user;
    private String application;
    private String clientAddress;
    private String state;
    private long durationSeconds;
    private String waitEventType;
    private String waitEvent;
    private String query;
    private List<String> lockedObjects = new ArrayList<>();
    private int affectedSessions;
    private List<PgBlockingNodeVo> children = new ArrayList<>();
}