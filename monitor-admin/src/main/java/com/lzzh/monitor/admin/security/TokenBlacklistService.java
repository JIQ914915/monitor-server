package com.lzzh.monitor.admin.security;

import com.lzzh.monitor.common.security.JwtProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;

/**
 * Token 黑名单服务（P0-4）。
 *
 * <p>使用 Redis 存储"用户级吊销"标记，解决 JWT 无状态鉴权时权限撤销无法实时生效的问题：
 * <ul>
 *   <li>禁用用户 / 用户密码重置后调用 {@link #revokeUser}，该用户的所有 JWT 立即失效；</li>
 *   <li>修改角色权限后，对持有该角色的所有用户调用 {@link #revokeUsers}，
 *       迫使他们重新登录以获取最新权限。</li>
 * </ul>
 *
 * <p><b>Redis Key 设计</b>：
 * <pre>
 *   token:user:blocked:{userId}  →  "1"，TTL = JWT 过期时长（默认 8h）
 * </pre>
 * TTL 对齐 JWT 有效期：超过有效期的 Token 本身已过期，黑名单记录自动清理，无需额外维护。
 *
 * <p>重新启用用户时可调用 {@link #clearRevocation} 提前解除封锁（可选），
 * 否则用户在 TTL 到期后可自行重新登录。
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String KEY_PREFIX = "token:user:blocked:";

    @Resource
    private StringRedisTemplate redisTemplate;
    @Resource
    private JwtProperties jwtProperties;

    private Duration tokenTtl;

    @PostConstruct
    void initTokenTtl() {
        this.tokenTtl = Duration.ofMillis(jwtProperties.getExpireMillis());
    }

    /**
     * 吊销指定用户的所有 Token。
     *
     * @param userId 用户 ID
     */
    public void revokeUser(Long userId) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, "1", tokenTtl);
            log.info("已吊销用户 {} 的 Token，TTL={}s", userId, tokenTtl.toSeconds());
        } catch (Exception e) {
            log.error("吊销用户 {} Token 失败: {}", userId, e.getMessage());
        }
    }

    /**
     * 批量吊销多个用户的 Token（角色权限变更场景使用）。
     *
     * @param userIds 用户 ID 集合
     */
    public void revokeUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        for (Long userId : userIds) {
            revokeUser(userId);
        }
        log.info("已批量吊销 {} 个用户的 Token", userIds.size());
    }

    /**
     * 检查指定用户的 Token 是否已被吊销。
     *
     * @param userId 用户 ID
     * @return true 表示已被吊销，应拒绝请求
     */
    public boolean isRevoked(Long userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + userId));
        } catch (Exception e) {
            // Redis 不可用时降级为放行，避免 Redis 故障导致全站不可用
            log.warn("检查 Token 黑名单失败（Redis 异常，降级放行）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 提前解除用户 Token 吊销（重新启用用户时可选调用）。
     *
     * @param userId 用户 ID
     */
    public void clearRevocation(Long userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
            log.info("已解除用户 {} 的 Token 吊销", userId);
        } catch (Exception e) {
            log.warn("解除用户 {} Token 吊销失败: {}", userId, e.getMessage());
        }
    }
}
