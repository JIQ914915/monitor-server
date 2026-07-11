package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * TsTopSqlQueryMapper 动态 SQL 语法自检。
 *
 * <p>{@code <script>} 注解 SQL 会按 XML 解析，SQL 中的 {@code <} 必须转义为 {@code &lt;}，
 * 否则应用启动时 MapperFactoryBean 注册即失败。本测试在构建期将 Mapper 注册进裸 MyBatis
 * 配置，任何 script XML 语法错误都会在此暴露，避免带病发布。
 */
class TsTopSqlQueryMapperScriptTest {

    @Test
    @DisplayName("script 动态 SQL 可被 MyBatis 正常解析注册")
    void mapperScriptsParse() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        assertDoesNotThrow(() -> configuration.addMapper(TsTopSqlQueryMapper.class),
                "TsTopSqlQueryMapper 注解 SQL 解析失败（检查 <script> 中的 < 是否已转义为 &lt;）");
        assertDoesNotThrow(() -> configuration.addMapper(TsSlowSqlSampleQueryMapper.class),
                "TsSlowSqlSampleQueryMapper 注解 SQL 解析失败（检查 <script> 中的 < 是否已转义为 &lt;）");
        assertDoesNotThrow(() -> configuration.addMapper(TsSlowSqlSampleWriterMapper.class),
                "TsSlowSqlSampleWriterMapper 注解 SQL 解析失败");
    }
}
