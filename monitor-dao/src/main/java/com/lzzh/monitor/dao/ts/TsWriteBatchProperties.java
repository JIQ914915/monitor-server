package com.lzzh.monitor.dao.ts;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 时序写入批次参数配置。
 */
@Component
@ConfigurationProperties(prefix = "collector.ts-write")
public class TsWriteBatchProperties {

    private int metricChunkSize = 200;
    private int textChunkSize = 200;
    private int topSqlChunkSize = 100;
    private int longConnChunkSize = 100;
    private int capacityObjectChunkSize = 200;
    private long slowLogMs = 2000;

    public int getMetricChunkSize() {
        return metricChunkSize;
    }

    public void setMetricChunkSize(int metricChunkSize) {
        this.metricChunkSize = metricChunkSize;
    }

    public int getTextChunkSize() {
        return textChunkSize;
    }

    public void setTextChunkSize(int textChunkSize) {
        this.textChunkSize = textChunkSize;
    }

    public int getTopSqlChunkSize() {
        return topSqlChunkSize;
    }

    public void setTopSqlChunkSize(int topSqlChunkSize) {
        this.topSqlChunkSize = topSqlChunkSize;
    }

    public int getLongConnChunkSize() {
        return longConnChunkSize;
    }

    public void setLongConnChunkSize(int longConnChunkSize) {
        this.longConnChunkSize = longConnChunkSize;
    }

    public int getCapacityObjectChunkSize() {
        return capacityObjectChunkSize;
    }

    public void setCapacityObjectChunkSize(int capacityObjectChunkSize) {
        this.capacityObjectChunkSize = capacityObjectChunkSize;
    }

    public long getSlowLogMs() {
        return slowLogMs;
    }

    public void setSlowLogMs(long slowLogMs) {
        this.slowLogMs = slowLogMs;
    }
}
