package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CollectLogMapperScriptTest {

    private static final String NAMESPACE = CollectLogMapper.class.getName();

    @Test
    void buildsDatabasePagingForTaskListAndHistory() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        try (InputStream resource = getClass().getClassLoader()
                .getResourceAsStream("mapper/CollectLogMapper.xml")) {
            assertThat(resource).isNotNull();
            new XMLMapperBuilder(resource, configuration, "mapper/CollectLogMapper.xml",
                    configuration.getSqlFragments()).parse();
        }

        Map<String, Object> taskParams = new HashMap<>();
        taskParams.put("keyword", "db-01");
        taskParams.put("dbType", null);
        taskParams.put("frequency", "1m");
        taskParams.put("status", "running");
        taskParams.put("offset", 20);
        taskParams.put("limit", 20);
        BoundSql taskPage = boundSql(configuration, "selectTaskPage", taskParams);
        assertThat(taskPage.getSql()).contains(
                "FROM task_candidates c", "JOIN LATERAL",
                "ORDER BY cl.collect_time DESC", "LIMIT 1", "FROM task_rows t", "lower(t.\"instanceName\") LIKE",
                "t.\"frequency\" = ?", "t.\"status\" = ?", "LIMIT ? OFFSET ?");
        assertThat(boundSql(configuration, "countTasks", taskParams).getSql())
                .contains("SELECT COUNT(*)", "FROM task_rows t").doesNotContain("LIMIT ? OFFSET ?");
        assertThat(boundSql(configuration, "selectTaskStats", taskParams).getSql())
                .contains("FILTER (WHERE t.\"status\" = 'running')");

        Map<String, Object> hostParams = historyParams(null, 9L);
        BoundSql hostHistory = boundSql(configuration, "selectRecent", hostParams);
        assertThat(hostHistory.getSql()).contains(
                "instance_id = 0", "host_id = ?", "LIMIT ? OFFSET ?")
                .doesNotContain("host_id IS NULL");

        Map<String, Object> instanceParams = historyParams(3L, null);
        BoundSql instanceHistory = boundSql(configuration, "selectRecent", instanceParams);
        assertThat(instanceHistory.getSql()).contains("instance_id = ?", "host_id IS NULL", "LIMIT ? OFFSET ?");
        assertThat(boundSql(configuration, "countRecent", instanceParams).getSql())
                .contains("SELECT COUNT(*)").doesNotContain("LIMIT");
    }

    private static Map<String, Object> historyParams(Long instanceId, Long hostId) {
        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("hostId", hostId);
        params.put("frequency", "1m");
        params.put("offset", 20);
        params.put("limit", 20);
        return params;
    }

    private static BoundSql boundSql(MybatisConfiguration configuration, String statement,
                                     Map<String, Object> params) {
        return configuration.getMappedStatement(NAMESPACE + "." + statement).getBoundSql(params);
    }
}