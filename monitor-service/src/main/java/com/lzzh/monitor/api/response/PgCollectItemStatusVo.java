package com.lzzh.monitor.api.response;

import lombok.Data;

import java.time.OffsetDateTime;

/** PostgreSQL 单项采集当前质量状态。 */
@Data
public class PgCollectItemStatusVo {
    private String frequency;
    private String itemCode;
    private String status;
    private String reason;
    private Integer durationMs;
    private Integer rowCount;
    private Integer consecutiveFailures;
    private OffsetDateTime lastSuccessAt;
    private OffsetDateTime collectedAt;
}
