package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsLongConnMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 长连接明细查询 DAO。
 * <p>从 {@code metric_long_conn} 读取最新一批长连接快照，
 * 用于实时概况「连接 Tab」的长连接列表。
 * <p>取每个 conn_id 最新一条（DISTINCT ON），再按持续时间降序展示。
 */
@Repository
public class TsLongConnDao {

    private final TsLongConnMapper mapper;

    public TsLongConnDao(TsLongConnMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询实例当前长连接列表。
     *
     * @param instanceId 实例 ID
     * @return 长连接按持续时间降序排列，最多 500 条
     */
    public List<LongConnRow> queryLatest(Long instanceId) {
        List<Map<String, Object>> rows = mapper.selectLatest(instanceId);
        List<LongConnRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Timestamp ts = (Timestamp) row.get("collect_time");
            result.add(new LongConnRow(
                    ((Number) row.get("conn_id")).longValue(),
                    (String) row.get("conn_user"),
                    (String) row.get("conn_host"),
                    (String) row.get("conn_db"),
                    (String) row.get("command"),
                    ((Number) row.get("time_seconds")).intValue(),
                    (String) row.get("state"),
                    (String) row.get("info"),
                    ts != null ? ts.toInstant().toEpochMilli() : 0L
            ));
        }
        return result;
    }

    /** 长连接单行数据。 */
    public record LongConnRow(
            long connId,
            String connUser,
            String connHost,
            String connDb,
            String command,
            int timeSeconds,
            String state,
            String info,
            long collectTimeMs
    ) {}
}
