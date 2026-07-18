package com.lzzh.monitor.service.sqlserver;

import com.lzzh.monitor.api.response.SqlServerDiagnosticsVo;
import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.ts.TsMetricObjectDao;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 聚合 SQL Server 对象级诊断指标；只读取平台时序数据，不连接或操作目标数据库。 */
@Service
public class SqlServerDiagnosticsService {
    private static final List<String> METRIC_CODES = List.of(
            "sqlserver.transaction.open_seconds", "sqlserver.transaction.sleeping_open",
            "sqlserver.blocking.wait_seconds", "sqlserver.blocking.chain_depth",
            "sqlserver.blocking.root_affected_sessions",
            "sqlserver.file.size_bytes", "sqlserver.file.used_bytes", "sqlserver.file.max_size_bytes",
            "sqlserver.file.growth_bytes", "sqlserver.file.growth_percent",
            "sqlserver.volume.total_bytes", "sqlserver.volume.available_bytes", "sqlserver.volume.free_percent",
            "sqlserver.log.vlf_count", "sqlserver.log.active_vlf_count",
            "sqlserver.tempdb.file_size_bytes", "sqlserver.tempdb.file_growth_bytes",
            "sqlserver.tempdb.file_growth_percent", "sqlserver.tempdb.volume_available_bytes",
            "sqlserver.query_store.plan_changed", "sqlserver.query_store.current_plan_count",
            "sqlserver.query_store.regression_ratio",
            "sqlserver.agent.job_enabled", "sqlserver.agent.job_status_code",
            "sqlserver.agent.job_duration_seconds", "sqlserver.agent.job_consecutive_failures",
            "sqlserver.agent.job_running_seconds",
            "sqlserver.replication.delivery_latency_ms", "sqlserver.cdc.scan_latency_seconds"
    );

    private final TsMetricObjectDao metricObjectDao;
    private final InstanceService instanceService;
    private final DataScopeService dataScopeService;

    public SqlServerDiagnosticsService(TsMetricObjectDao metricObjectDao,
                                       InstanceService instanceService,
                                       DataScopeService dataScopeService) {
        this.metricObjectDao = metricObjectDao;
        this.instanceService = instanceService;
        this.dataScopeService = dataScopeService;
    }

    public SqlServerDiagnosticsVo overview(Long instanceId) {
        requireSqlServerInstance(instanceId);
        Map<String, List<SqlServerDiagnosticsVo.Item>> metrics = new LinkedHashMap<>();
        for (String metricCode : METRIC_CODES) {
            metrics.put(metricCode, metricObjectDao.queryTopN(instanceId, metricCode, 200)
                    .stream().map(SqlServerDiagnosticsService::item).toList());
        }
        SqlServerDiagnosticsVo result = new SqlServerDiagnosticsVo();
        result.setInstanceId(instanceId);
        result.setMetrics(metrics);
        return result;
    }

    private void requireSqlServerInstance(Long instanceId) {
        if (instanceId == null || !dataScopeService.currentScope().allows(instanceId)) {
            throw new BusinessException("无权访问该实例");
        }
        var target = instanceService.getCollectTarget(instanceId);
        if (target == null) {
            throw new BusinessException("实例不存在");
        }
        if (DbType.of(target.getDbType()) != DbType.SQLSERVER) {
            throw new BusinessException("该诊断功能仅支持 SQL Server 实例");
        }
    }

    private static SqlServerDiagnosticsVo.Item item(TsMetricObjectDao.ObjectPoint point) {
        SqlServerDiagnosticsVo.Item item = new SqlServerDiagnosticsVo.Item();
        item.setObjectName(point.objectName());
        item.setObjectType(point.objectType());
        item.setValue(point.value());
        item.setCollectTimeMs(point.collectTimeMs());
        return item;
    }
}