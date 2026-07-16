package com.lzzh.monitor.dao.ts;

import java.util.List;

/** Top SQL / digest 累积快照 + 差值写入（§21.2.5 对象级专用表 metric_top_sql，P1-3）。 */
public interface TsTopSqlWriter {

    /**
     * 批量写入 Top SQL 差值点。
     * <p>调用方应确保列表中的点均已完成差值计算（{@code hasDelta() == true}）；
     * 首次采样或计数器回绕的点（{@code hasDelta() == false}）由写入层过滤丢弃，不落库。
     */
    void batchWrite(List<TsTopSqlPoint> points);

    /** Top SQL 落库形态（含差值字段）。 */
    record TsTopSqlPoint(Long instanceId, String schemaName, String digest, String digestText,
                         long countStar, long sumTimerWait, long rowsExamined, long rowsSent,
                         // 差值字段（null = 首次采样 / 计数器回绕，写入层跳过本条）
                         Long deltaCount, Long deltaTimerWait, Long avgTimerWaitUs,
                         Long deltaRowsExamined, Long deltaRowsSent,
                         // 诊断维度差值（锁等待/排序行/无索引/临时表，V99）
                         Long deltaLockTime, Long deltaSortRows, Long deltaNoIndexUsed,
                         Long deltaTmpTables, Long deltaTmpDiskTables,
                         Long deltaPhysicalReads, Long deltaWrites,
                         long timestampMillis) {

        /** 是否携带有效差值（false 则不应落库）。 */
        public boolean hasDelta() {
            return deltaCount != null;
        }
    }
}
