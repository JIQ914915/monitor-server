package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.api.request.PgSessionActionRequest;
import com.lzzh.monitor.api.request.PgSessionQueryRequest;
import com.lzzh.monitor.api.response.PgBlockingNodeVo;
import com.lzzh.monitor.api.response.PgDatabaseVo;
import com.lzzh.monitor.api.response.PgSessionVo;
import com.lzzh.monitor.common.result.PageResult;
import java.util.List;

public interface PostgreSqlDiagnosticService {
    List<PgDatabaseVo> databases(Long instanceId);
    PageResult<PgSessionVo> sessions(PgSessionQueryRequest request);
    List<PgBlockingNodeVo> blockingTree(Long instanceId);
    boolean cancel(PgSessionActionRequest request);
    boolean terminate(PgSessionActionRequest request);
}
