package com.lzzh.monitor.dao.ts;

import java.util.List;

/** 慢 SQL 真实执行样本写入接口（metric_slow_sql_sample 超表）。 */
public interface TsSlowSqlSampleWriter {

    /**
     * 批量追加慢 SQL 样本。
     * <p>空列表时直接返回，不执行 SQL。
     */
    void batchWrite(Long instanceId, List<TsSlowSqlSamplePoint> points);

    /** 慢 SQL 样本落库形态。 */
    record TsSlowSqlSamplePoint(
            long threadId,
            long eventId,
            String connUser,
            String connHost,
            String schemaName,
            String digest,
            String sqlText,
            long execTimeUs,
            long lockTimeUs,
            long rowsExamined,
            long rowsSent,
            long sortRows,
            boolean noIndexUsed,
            long tmpTables,
            long tmpDiskTables,
            long timestampMillis
    ) {}
}
