package com.lzzh.monitor.api.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** SQL Server 诊断对象指标最新快照。 */
@Data
public class SqlServerDiagnosticsVo {
    private Long instanceId;
    private Map<String, List<Item>> metrics;

    @Data
    public static class Item {
        private String objectName;
        private String objectType;
        private double value;
        private long collectTimeMs;
    }
}