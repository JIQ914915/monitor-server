package com.lzzh.monitor.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SlowSqlPlanHistoryRequest {
    @NotNull(message = "instanceId 不能为空") private Long instanceId;
    @NotBlank(message = "schemaName 不能为空") private String schemaName;
    @NotBlank(message = "sqlHash 不能为空") private String sqlHash;
}
