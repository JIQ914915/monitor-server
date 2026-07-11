package com.lzzh.monitor.dao.ts;

import java.util.List;

/** 长连接明细写入接口（metric_long_conn 超表）。 */
public interface TsLongConnWriter {

    /**
     * 批量追加长连接明细快照。
     * <p>空列表时直接返回，不执行 SQL。
     *
     * @param instanceId 实例 ID
     * @param points     本轮采集到的长连接列表（time >= 阈值的非 Sleep 连接）
     */
    void batchWrite(Long instanceId, List<TsLongConnPoint> points);

    /** 长连接明细落库形态。 */
    record TsLongConnPoint(
            long connId,
            String connUser,
            String connHost,
            String connDb,
            String command,
            int timeSeconds,
            String state,
            String info,
            long timestampMillis
    ) {}
}
