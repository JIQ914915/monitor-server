package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDiagnosticMapperScriptTest {
    @Test void parsesAllP0P1Statements() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        String path = "mapper/MySqlDiagnosticMapper.xml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(in).isNotNull();
            new XMLMapperBuilder(in, configuration, path, configuration.getSqlFragments()).parse();
        }
        String namespace = MySqlDiagnosticMapper.class.getName() + ".";
        assertThat(configuration.hasStatement(namespace + "selectConfigChanges")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectAutoIncrementRisks")).isTrue();
        assertThat(configuration.hasStatement(namespace + "insertPlan")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectPlanHistory")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectRecentPlans")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectLatestSecuritySnapshotHash")).isTrue();
        assertThat(configuration.hasStatement(namespace + "insertSecuritySnapshot")).isTrue();
    }
}
