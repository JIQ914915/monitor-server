package com.lzzh.monitor.collector.spi.model;

import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.common.enums.DbType;
import lombok.Data;

import java.util.List;

/** 采集请求：交给采集器执行的输入。 */
@Data
public class CollectRequest {
    private Long instanceId;
    private DbType dbType;
    private String version;
    private TargetDataSource target;
    private CollectFrequency frequency;
    /** 本次需采集的采集项编码（可插拔，按版本能力矩阵过滤）。 */
    private List<String> collectItems;
    /**
     * 连接来源白名单（IP 精确匹配或 "10.0.1.*" 前缀通配，空 = 未启用白名单检测）。
     * 由实例管理维护（db_instance.conn_source_whitelist），ProcesslistItem 据此产出未知来源指标。
     */
    private List<String> connSourceWhitelist;
}
