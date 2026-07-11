package com.lzzh.monitor.service.datascope;

import java.util.List;

/**
 * 跨模块传递"当前登录用户"的轻量级 ThreadLocal 载体。
 *
 * <p>monitor-service / monitor-collector 不直接依赖 Spring Security；
 * Web 层（monitor-admin 的 JwtAuthFilter）在鉴权通过后写入本载体，
 * 请求处理结束（无论成败）必须在 finally 中清理，避免容器线程复用导致串号。
 *
 * <p>未设置时（如 XXL-JOB 后台任务线程、单元测试）视为系统调用，
 * {@link DataScopeService#currentScope()} 按全量不受限处理。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<Current> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    /** @param userId 用户ID @param roles 角色编码集合 */
    public record Current(Long userId, List<String> roles) {
    }

    public static void set(Long userId, List<String> roles) {
        HOLDER.set(new Current(userId, roles));
    }

    public static Current get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
