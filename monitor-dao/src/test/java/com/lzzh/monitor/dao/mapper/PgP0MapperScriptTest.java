package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.lzzh.monitor.dao.ts.PgCollectItemStatusWriter;
import com.lzzh.monitor.dao.ts.TsPgOperationalEventWriter;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PgP0MapperScriptTest {

    @Test
    void qualityAndOperationalSnapshotMappersBuildBoundSql() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        parse(configuration, "mapper/PgCollectItemStatusWriterMapper.xml");
        parse(configuration, "mapper/TsPgOperationalEventWriterMapper.xml");
        parse(configuration, "mapper/PgOperationsMapper.xml");

        var quality = new PgCollectItemStatusWriter.ItemStatus(
                "1m", "connections", "success", "none", 12, 8, 1_700_000_000_000L);
        BoundSql qualitySql = boundSql(configuration,
                PgCollectItemStatusWriterMapper.class.getName() + ".upsertBatch",
                Map.of("instanceId", 1L, "items", List.of(quality)));
        assertThat(normalize(qualitySql.getSql())).contains(
                "INSERT INTO pg_collect_item_status", "ON CONFLICT (instance_id,frequency,item_code)",
                "consecutive_failures=CASE");

        var event = new TsPgOperationalEventWriter.TsPgOperationalEvent(
                "postgresql", "replication", "physical_sender", "info", "postgres", null,
                "standby-1", null, null, "streaming", "fingerprint", "{}", true,
                1_700_000_000_000L);
        Map<String, Object> writeParams = Map.of("instanceId", 1L, "items", List.of(event));
        BoundSql stateChange = boundSql(configuration,
                TsPgOperationalEventWriterMapper.class.getName() + ".insertStateChanges", writeParams);
        assertThat(normalize(stateChange.getSql())).contains(
                "LEFT JOIN pg_operational_snapshot", "s.severity IS DISTINCT FROM i.severity");
        BoundSql snapshot = boundSql(configuration,
                TsPgOperationalEventWriterMapper.class.getName() + ".upsertSnapshots", writeParams);
        assertThat(normalize(snapshot.getSql())).contains(
                "INSERT INTO pg_operational_snapshot", "ON CONFLICT (instance_id,fingerprint) DO UPDATE");

        Map<String, Object> query = new HashMap<>();
        query.put("instanceId", 1L);
        query.put("category", "replication");
        query.put("from", Timestamp.from(Instant.now().minusSeconds(3600)));
        query.put("to", Timestamp.from(Instant.now()));
        query.put("limit", 20);
        query.put("offset", 0L);
        BoundSql page = boundSql(configuration,
                PgOperationsMapper.class.getName() + ".selectSnapshots", query);
        assertThat(normalize(page.getSql())).contains(
                "FROM pg_operational_snapshot", "category=?", "LIMIT ? OFFSET ?");
    }

    private static void parse(MybatisConfiguration configuration, String resourcePath) throws Exception {
        try (InputStream resource = PgP0MapperScriptTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(resource).as(resourcePath).isNotNull();
            new XMLMapperBuilder(resource, configuration, resourcePath, configuration.getSqlFragments()).parse();
        }
    }

    private static BoundSql boundSql(MybatisConfiguration configuration, String id, Map<String, ?> parameters) {
        assertThat(configuration.hasStatement(id, false)).as(id).isTrue();
        return configuration.getMappedStatement(id, false).getBoundSql(parameters);
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
