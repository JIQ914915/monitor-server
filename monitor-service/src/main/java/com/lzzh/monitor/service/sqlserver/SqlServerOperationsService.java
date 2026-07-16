package com.lzzh.monitor.service.sqlserver;
import com.lzzh.monitor.api.request.SqlServerRestoreDrillPageRequest;
import com.lzzh.monitor.api.request.SqlServerRestoreDrillRequest;
import com.lzzh.monitor.api.response.SqlServerRestoreDrillVo;
import com.lzzh.monitor.common.result.PageResult;
public interface SqlServerOperationsService {
 PageResult<SqlServerRestoreDrillVo> restoreDrills(SqlServerRestoreDrillPageRequest request);
 SqlServerRestoreDrillVo saveRestoreDrill(SqlServerRestoreDrillRequest request);
}
