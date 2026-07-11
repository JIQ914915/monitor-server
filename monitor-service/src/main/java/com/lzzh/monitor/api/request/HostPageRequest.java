package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 主机分页查询（请求）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "主机分页查询入参")
public class HostPageRequest extends PageParam {

    @Schema(description = "状态过滤：normal / abnormal / paused", example = "normal")
    private String status;

    @Schema(description = "采集方式过滤：exporter / ssh / none", example = "exporter")
    private String collectMode;
}
