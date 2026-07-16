package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.request.SlowSqlExplainRequest;
import com.lzzh.monitor.api.request.SlowSqlPlanHistoryRequest;
import com.lzzh.monitor.api.response.MySqlPlanHistoryVo;
import com.lzzh.monitor.api.response.SlowSqlExplainVo;

import java.util.List;

/**
 * 慢 SQL 实时执行计划服务：使用实例的采集账号连到目标库执行 EXPLAIN。
 * <p>EXPLAIN 只做优化器分析不真正执行语句（MySQL 对 SELECT/DML 均如此），
 * 属只读操作；服务端仍做语句白名单与单语句校验，防止借道执行任意 SQL。
 */
public interface SlowSqlExplainService {

    /** 连目标库执行 EXPLAIN，返回列名 + 行值透传结果。失败抛 BusinessException（面向用户的错误信息）。 */
    SlowSqlExplainVo explain(SlowSqlExplainRequest request);

    List<MySqlPlanHistoryVo> history(SlowSqlPlanHistoryRequest request);
}
