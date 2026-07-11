package com.lzzh.monitor.service.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯 Mockito 单测（不启动 Spring 容器）里构造 {@code LambdaQueryWrapper}/{@code LambdaUpdateWrapper}
 * 会抛出 {@code "MybatisPlus can not find lambda cache for this entity"}——因为
 * 实体的字段-列名映射（{@link com.baomidou.mybatisplus.core.metadata.TableInfo}）平时是
 * Spring Boot 启动扫描 Mapper 时惰性建立的。这里手动触发同一套初始化逻辑，让被测服务里
 * 直接 {@code new LambdaQueryWrapper<Entity>()} 的写法无需改造即可在纯单测环境下工作。
 */
public final class MybatisPlusTestSupport {

    private MybatisPlusTestSupport() {
    }

    public static void ensureTableInfo(Class<?>... entityClasses) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entityClass : entityClasses) {
            if (TableInfoHelper.getTableInfo(entityClass) != null) {
                continue;
            }
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace(entityClass.getName());
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }
}
