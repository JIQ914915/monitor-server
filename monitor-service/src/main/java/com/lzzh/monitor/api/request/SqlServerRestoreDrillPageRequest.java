package com.lzzh.monitor.api.request;
import com.lzzh.monitor.common.result.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
public class SqlServerRestoreDrillPageRequest extends PageParam {@NotNull private Long instanceId;}
