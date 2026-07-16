package com.lzzh.monitor.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MySqlConfigDriftRequest {
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;
    private Long compareInstanceId;
    private String baselineTemplate = "stability";
}