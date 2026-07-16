package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PgDiagnosticMapperScriptTest {
    private static final String NAMESPACE = PgDiagnosticMapper.class.getName();

    @Test
    void mapperXmlParsesAndBuildsAnalyticsQuery() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        try (InputStream resource = getClass().getClassLoader()
                .getResourceAsStream("mapper/PgDiagnosticMapper.xml")) {
            assertThat(resource).isNotNull();
            new XMLMapperBuilder(resource, configuration, "mapper/PgDiagnosticMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", 1L);
        params.put("from", OffsetDateTime.now().minusDays(1));
        params.put("to", OffsetDateTime.now());
        params.put("database", "postgres");
        params.put("user", null);
        params.put("queryId", null);
        params.put("orderBy", "total_exec_time_ms DESC");
        params.put("limit", 100);
        params.put("offset", 20);

        BoundSql sql = configuration.getMappedStatement(NAMESPACE + ".selectQueryAnalytics")
                .getBoundSql(params);
        assertThat(sql.getSql()).contains("FROM pg_query_stat_history", "database_name=?",
                "ORDER BY total_exec_time_ms DESC", "LIMIT ? OFFSET ?")
                .doesNotContain("user_name=?");

        BoundSql countSql = configuration.getMappedStatement(NAMESPACE + ".countQueryAnalytics")
                .getBoundSql(params);
        assertThat(countSql.getSql())
                .contains("SELECT count(*)", "GROUP BY database_name,user_name,query_id", "database_name=?")
                .doesNotContain("LIMIT", "OFFSET", "ORDER BY");
    }
}