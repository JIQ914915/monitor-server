package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.request.SlowSqlExplainRequest;
import com.lzzh.monitor.api.request.SlowSqlPlanHistoryRequest;
import com.lzzh.monitor.api.response.MySqlPlanHistoryVo;
import com.lzzh.monitor.api.response.SlowSqlExplainVo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/** MySQL 执行计划接口适配：统一委托给结构化计划服务。 */
@Service
public class SlowSqlExplainServiceImpl implements SlowSqlExplainService {

    @Resource
    private MySqlPlanService mySqlPlanService;

    @Override
    public SlowSqlExplainVo explain(SlowSqlExplainRequest request) {
        return mySqlPlanService.explain(request);
    }

    @Override
    public List<MySqlPlanHistoryVo> history(SlowSqlPlanHistoryRequest request) {
        return mySqlPlanService.history(request);
    }
}