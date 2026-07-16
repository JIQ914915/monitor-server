package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.api.request.PgPageRequest;
import com.lzzh.monitor.api.request.PgPlanCaptureRequest;
import com.lzzh.monitor.api.request.PgQueryAnalyticsRequest;
import com.lzzh.monitor.api.response.PgAdvisorVo;
import com.lzzh.monitor.api.response.PgObjectAnalysisVo;
import com.lzzh.monitor.api.response.PgPlanHistoryVo;
import com.lzzh.monitor.api.response.PgQueryAnalyticsVo;
import com.lzzh.monitor.api.response.PgSqlRegressionVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

public interface PostgreSqlPhase2Service {
    PageResult<PgQueryAnalyticsVo> queryAnalytics(PgQueryAnalyticsRequest request);
    PageResult<PgSqlRegressionVo> regressions(PgPageRequest request);
    PgPlanHistoryVo capturePlan(PgPlanCaptureRequest request);
    List<PgPlanHistoryVo> planHistory(Long instanceId, String database, String queryId);
    List<PgAdvisorVo> vacuumAdvisor(Long instanceId);
    List<PgAdvisorVo> indexAdvisor(Long instanceId);
    PageResult<PgObjectAnalysisVo> objects(PgPageRequest request);
}
