package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.api.request.PgOperationalEventQuery;
import com.lzzh.monitor.api.request.PgPageRequest;
import com.lzzh.monitor.api.request.PgRestoreDrillRequest;
import com.lzzh.monitor.api.response.PgOperationalEventVo;
import com.lzzh.monitor.api.response.PgOperationalHealthVo;
import com.lzzh.monitor.api.response.PgOperationalSummaryVo;
import com.lzzh.monitor.api.response.PgRestoreDrillVo;
import com.lzzh.monitor.common.result.PageResult;
import java.util.List;

public interface PostgreSqlPhase3Service {
    PageResult<PgOperationalEventVo> events(PgOperationalEventQuery request,String forcedSource,String forcedCategory,boolean excludeAudit);
    List<PgOperationalSummaryVo> summary(Long instanceId);
    PgOperationalHealthVo health(Long instanceId);
    PageResult<PgRestoreDrillVo> restoreDrills(PgPageRequest request);
    List<PgRestoreDrillVo> latestRestoreDrills(Long instanceId, int limit);
    PgRestoreDrillVo saveRestoreDrill(PgRestoreDrillRequest request);
}
