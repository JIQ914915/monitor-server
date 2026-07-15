package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.api.request.PgPlanCaptureRequest;
import com.lzzh.monitor.api.request.PgQueryAnalyticsRequest;
import com.lzzh.monitor.api.response.PgAdvisorVo;
import com.lzzh.monitor.api.response.PgObjectAnalysisVo;
import com.lzzh.monitor.api.response.PgPlanHistoryVo;
import com.lzzh.monitor.api.response.PgQueryAnalyticsVo;
import com.lzzh.monitor.api.response.PgSqlRegressionVo;

import java.util.List;

public interface PostgreSqlPhase2Service {
    List<PgQueryAnalyticsVo> queryAnalytics(PgQueryAnalyticsRequest request);
    List<PgSqlRegressionVo> regressions(Long instanceId);
    PgPlanHistoryVo capturePlan(PgPlanCaptureRequest request);
    List<PgPlanHistoryVo> planHistory(Long instanceId, String database, String queryId);
    List<PgAdvisorVo> vacuumAdvisor(Long instanceId);
    List<PgAdvisorVo> indexAdvisor(Long instanceId);
    List<PgObjectAnalysisVo> objects(Long instanceId);
}