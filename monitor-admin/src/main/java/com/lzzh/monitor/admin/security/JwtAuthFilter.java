package com.lzzh.monitor.admin.security;

import com.lzzh.monitor.common.security.JwtProperties;
import com.lzzh.monitor.common.security.JwtUtil;
import com.lzzh.monitor.dao.entity.SysUser;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.service.datascope.CurrentUserHolder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析令牌 → 检查黑名单 → 加载用户 → 计算权限并集 → 写入 SecurityContext。
 *
 * <p><b>P0-4 改造</b>：在用户信息加载后，额外调用 {@link TokenBlacklistService#isRevoked}
 * 检查该用户是否已被主动吊销（禁用用户或角色权限变更时写入 Redis）。
 * 吊销的用户请求主动返回 401，而非等 JWT 自然过期（最长 8 小时）。
 *
 * <p>Redis 不可用时 {@link TokenBlacklistService} 降级放行，不影响正常鉴权。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtProperties props;
    private final SysUserMapper userMapper;
    private final RolePermissionResolver permissionResolver;
    private final TokenBlacklistService blacklistService;

    public JwtAuthFilter(JwtUtil jwtUtil, JwtProperties props,
                         SysUserMapper userMapper, RolePermissionResolver permissionResolver,
                         TokenBlacklistService blacklistService) {
        this.jwtUtil = jwtUtil;
        this.props = props;
        this.userMapper = userMapper;
        this.permissionResolver = permissionResolver;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(props.getHeader());
        String token = jwtUtil.resolve(header);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.parse(token);
                Long uid = claims.get("uid", Long.class);
                SysUser user = uid != null ? userMapper.selectById(uid) : null;

                if (user != null && Boolean.TRUE.equals(user.getEnabled())) {
                    // P0-4：检查该用户是否已被主动吊销（禁用/角色变更）
                    if (blacklistService.isRevoked(uid)) {
                        writeUnauthorized(response, "账号权限已变更，请重新登录");
                        return;
                    }
                    List<String> perms = permissionResolver.resolve(user.getRoles());
                    LoginUser principal = new LoginUser(user.getId(), user.getUsername(),
                            user.getNickname(), user.getRoles(), perms);
                    var authorities = perms.stream().map(SimpleGrantedAuthority::new).toList();
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    // 数据范围校验组件（DataScopeService）通过本 ThreadLocal 读取当前用户，
                    // 使 monitor-service 无需依赖 Spring Security 即可统一做实例级数据范围过滤。
                    CurrentUserHolder.set(user.getId(), user.getRoles());
                }
            } catch (ExpiredJwtException e) {
                writeUnauthorized(response, "登录已过期，请重新登录");
                return;
            } catch (Exception ignored) {
                // token 格式/签名错误 → 由 Security ExceptionTranslationFilter 返回 401
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 容器线程池复用：请求结束必须清理，避免下一个请求误用上一个用户的数据范围
            CurrentUserHolder.clear();
        }
    }

    private static void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
