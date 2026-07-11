package com.lzzh.monitor.admin.config;

import com.lzzh.monitor.admin.security.JwtAuthFilter;
import com.lzzh.monitor.common.security.JwtProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Spring Security + JWT 无状态鉴权配置。 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** 放行：登录、API 文档（Knife4j / springdoc 相关路径全部放行）。其余需认证。 */
    private static final String[] WHITELIST = {
            "/api/v1/auth/login",
            // Knife4j 文档页及静态资源
            "/doc.html",
            "/webjars/**",
            "/favicon.ico",
            // springdoc OpenAPI 数据接口
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            // Swagger UI（默认路径）
            "/swagger-ui/**",
            "/swagger-ui.html",
            // springdoc swagger-resources（Knife4j 调用）
            "/swagger-resources",
            "/swagger-resources/**"
    };

    @Resource
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(WHITELIST).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // 未认证（无 token 或 token 格式/签名错误）→ 401 JSON
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        401, "未认证或登录已过期"))
                        // 已认证但无权限（Spring Security 层面，非 @RequiresPerm 切面）→ 403 JSON
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        403, "无权限")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 写出 HTTP status + 统一 JSON 响应体，格式与 Result<Void> 一致。 */
    private static void writeJson(HttpServletResponse response, int httpStatus,
                                  int code, String msg) throws java.io.IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
