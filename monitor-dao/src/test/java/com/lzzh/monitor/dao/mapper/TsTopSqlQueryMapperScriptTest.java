package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * TsTopSqlQueryMapper 动态 SQL 语法自检。
 *
 * <p>从 Mapper XML 加载语句，验证 schema 过滤、排序、分页与参数化行为。
 */
class TsTopSqlQueryMapperScriptTest {

    private static final String NAMESPACE = TsTopSqlQueryMapper.class.getName();

    @Test
    @DisplayName("TsTopSqlQueryMapper XML 动态 SQL 可被 MyBatis 正常解析并生成预期 BoundSql")
    void mapperXmlBuildsBoundSqlForTopSqlStatements() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        parseMapper(configuration, "mapper/TsTopSqlQueryMapper.xml");
        parseMapper(configuration, "mapper/TsSlowSqlSampleQueryMapper.xml");
        parseMapper(configuration, "mapper/TsSlowSqlSampleWriterMapper.xml");

        Timestamp from = Timestamp.valueOf("2026-01-01 00:00:00");
        Timestamp to = Timestamp.valueOf("2026-01-02 00:00:00");

        Map<String, Object> digestPageParams = new HashMap<>();
        digestPageParams.put("instanceId", 1L);
        digestPageParams.put("from", from);
        digestPageParams.put("to", to);
        digestPageParams.put("keyword", "SELECT");
        digestPageParams.put("schemaName", "app_db");
        digestPageParams.put("sqlTypePrefix", "SELECT%");
        digestPageParams.put("minAvgUs", 1000L);
        digestPageParams.put("maxAvgUs", 50000L);
        digestPageParams.put("orderBy", "total_timer_wait DESC");
        digestPageParams.put("limit", 20);
        digestPageParams.put("offset", 0);
        BoundSql digestPage = boundSql(configuration, NAMESPACE + ".selectDigestPage", digestPageParams);
        assertThat(digestPage.getSql()).contains(
                "FROM metric_top_sql",
                "schema_name = ?",
                "digest_text ILIKE",
                "upper(digest_text) LIKE",
                "GROUP BY schema_name, digest",
                "HAVING",
                "ORDER BY total_timer_wait DESC",
                "LIMIT ?",
                "OFFSET ?");
        assertThat(digestPage.getParameterMappings()).hasSizeGreaterThanOrEqualTo(9);

        Map<String, Object> countDigestParams = new HashMap<>();
        countDigestParams.put("instanceId", 1L);
        countDigestParams.put("from", from);
        countDigestParams.put("to", to);
        countDigestParams.put("keyword", "");
        countDigestParams.put("schemaName", "app_db");
        countDigestParams.put("sqlTypePrefix", null);
        countDigestParams.put("minAvgUs", null);
        countDigestParams.put("maxAvgUs", null);
        BoundSql countDigest = boundSql(configuration, NAMESPACE + ".countDigest", countDigestParams);
        assertThat(countDigest.getSql()).contains("schema_name = ?", "GROUP BY schema_name, digest");
        assertThat(countDigest.getSql()).doesNotContain("HAVING");

        Map<String, Object> recordsPageParams = new HashMap<>();
        recordsPageParams.put("instanceId", 1L);
        recordsPageParams.put("from", from);
        recordsPageParams.put("to", to);
        recordsPageParams.put("sqlTypePrefix", "UPDATE%");
        recordsPageParams.put("minAvgUs", 500L);
        recordsPageParams.put("maxAvgUs", null);
        recordsPageParams.put("digest", "abc123");
        recordsPageParams.put("targetSchema", "app_db");
        recordsPageParams.put("limit", 10);
        recordsPageParams.put("offset", 5);
        BoundSql recordsPage = boundSql(configuration, NAMESPACE + ".selectRecordsPage", recordsPageParams);
        assertThat(recordsPage.getSql()).contains(
                "digest = ?",
                "schema_name IS NOT DISTINCT FROM",
                "ORDER BY collect_time DESC, avg_timer_wait_us DESC",
                "LIMIT ?",
                "OFFSET ?");

        BoundSql digestDetail = boundSql(configuration, NAMESPACE + ".selectDigestDetail", Map.of(
                "instanceId", 1L,
                "from", from,
                "to", to,
                "targetSchema", "app_db",
                "digest", "abc123"));
        assertThat(digestDetail.getSql()).contains("digest = ?", "LIMIT 1");

        BoundSql schemaNames = boundSql(configuration, NAMESPACE + ".selectSchemaNames", Map.of(
                "instanceId", 1L,
                "from", from,
                "to", to));
        assertThat(schemaNames.getSql()).contains(
                "DISTINCT schema_name",
                "schema_name IS NOT NULL",
                "ORDER BY schema_name",
                "LIMIT 200");
        assertThat(schemaNames.getParameterMappings()).hasSize(3);
    }

    @Test
    @DisplayName("相关慢 SQL Mapper XML 可被 MyBatis 正常解析注册")
    void relatedMapperXmlParses() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        assertDoesNotThrow(() -> {
            parseMapper(configuration, "mapper/TsSlowSqlSampleQueryMapper.xml");
            parseMapper(configuration, "mapper/TsSlowSqlSampleWriterMapper.xml");
        });
    }

    private static void parseMapper(MybatisConfiguration configuration, String resourcePath) throws Exception {
        try (InputStream resource = TsTopSqlQueryMapperScriptTest.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertThat(resource).as(resourcePath).isNotNull();
            new XMLMapperBuilder(resource, configuration, resourcePath, configuration.getSqlFragments()).parse();
        }
    }

    private static BoundSql boundSql(MybatisConfiguration configuration, String statementId,
                                     Map<String, ?> parameters) {
        assertThat(configuration.hasStatement(statementId, false)).as(statementId).isTrue();
        return configuration.getMappedStatement(statementId, false).getBoundSql(parameters);
    }
}
