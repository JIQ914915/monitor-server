package com.lzzh.monitor.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 文档（Knife4j 4.x / springdoc-openapi 2.x）。
 *
 * <p>关键：
 * <ol>
 *   <li>{@link OpenAPI} Bean —— 设置文档标题/版本等元信息。</li>
 *   <li>{@link GroupedOpenApi} Bean —— 显式告知 springdoc 扫描哪个包，
 *       否则 Knife4j 文档页面会空白（无接口）。</li>
 * </ol>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI monitorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("数据库监控平台 API")
                .description("monitor-admin Web API；业务接口统一前缀 /api/v1，破坏性变更时并列挂载 /api/v2")
                .version("v1"));
    }

    /**
     * 指定 springdoc 扫描的 Controller 包路径。
     * <p>不配置此 Bean 时 Knife4j 文档页面会打开但无任何接口，
     * 因为 springdoc 不知道从哪里扫描 {@code @RestController}。
     */
    @Bean
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("monitor-admin")
                .packagesToScan("com.lzzh.monitor.admin.controller")
                .build();
    }
}
