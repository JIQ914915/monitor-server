package com.lzzh.monitor.dao.ts;

import java.util.List;

/** 对象级容量明细写入（§21.2.5 对象级专用表 metric_capacity_object）。 */
public interface TsCapacityObjectWriter {

    /** 批量写入对象级容量点。 */
    void batchWrite(List<TsCapacityObjectPoint> points);

    /** 对象级容量点落库形态。 */
    record TsCapacityObjectPoint(Long instanceId, String metric, String objectType, String objectName,
                                 double value, long timestampMillis) {
    }
}
