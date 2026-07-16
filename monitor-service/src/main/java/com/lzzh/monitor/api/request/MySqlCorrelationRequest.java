package com.lzzh.monitor.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MySqlCorrelationRequest {
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;
    private Long from;
    private Long to;
}
