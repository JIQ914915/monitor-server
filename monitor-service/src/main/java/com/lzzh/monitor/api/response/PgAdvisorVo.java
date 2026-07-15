package com.lzzh.monitor.api.response;

import lombok.Data;

import java.util.Map;

@Data
public class PgAdvisorVo {
    private String category;
    private String database;
    private String objectName;
    private String severity;
    private String observationWindow;
    private String evidence;
    private String action;
    private String risk;
    private Map<String, Object> details;
}