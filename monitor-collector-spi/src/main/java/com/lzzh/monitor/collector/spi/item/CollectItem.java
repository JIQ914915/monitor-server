package com.lzzh.monitor.collector.spi.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.MetricPoint;

import java.util.List;

/**
 * 采集项 SPI（可插拔）：一个采集项一个实现，按 dbType+version 能力矩阵启用。
 * 新增采集项 = 实现本接口并注册，不改采集主流程（§5.8）。
 */
public interface CollectItem {

    /** 采集项编码，如 global_status、processlist、innodb_metrics。 */
    String code();

    /** 执行采集，产出指标点。 */
    List<MetricPoint> collect(CollectRequest request);
}
