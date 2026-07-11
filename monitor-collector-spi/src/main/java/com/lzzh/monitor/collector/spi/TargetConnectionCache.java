package com.lzzh.monitor.collector.spi;

import com.lzzh.monitor.collector.spi.model.TargetDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 目标库连接缓存（类型无关，MySQL / PostgreSQL 共用）。
 *
 * <p>每个被监控实例保持一条长连接，避免每轮采集都经历 TCP 握手 + 认证的开销。
 * 原位于 monitor-collector-mysql，多数据库支持后上移到 SPI 层；
 * 连接属性按 JDBC URL 前缀做驱动适配（MySQL 超时单位为毫秒、PostgreSQL 为秒）。
 *
 * <p><b>可靠性</b>：
 * <ul>
 *   <li><b>重试建连</b>：{@link #borrow} 内部最多重试 {@value #MAX_BORROW_RETRIES} 次，
 *       每次重试前等待 {@value #RETRY_INTERVAL_MS}ms，防止单次瞬断导致本轮采集整体失败。</li>
 *   <li><b>定时健康检查</b>：每 {@value #HEALTH_CHECK_INTERVAL_MS}ms 遍历缓存，
 *       主动 ping 每条连接，失活则驱逐，使下轮 {@link #borrow} 可立即重建。</li>
 * </ul>
 *
 * <p><b>使用约定</b>：
 * <ul>
 *   <li><b>必须先持有 {@link #instanceLock} 再 {@link #borrow} 并在整个使用期间持锁</b>：
 *       JDBC {@link Connection} 非线程安全，采集 Job（线程池）与告警自定义 SQL 评估（xxl-job 线程）
 *       可能同时访问同一实例，实例级互斥锁保证同一连接同一时刻只被一个线程使用。</li>
 *   <li>调用 {@link #borrow} 获取连接后，<b>不得手动 close</b>，由缓存统一管理。</li>
 *   <li>确认连接已损坏时须调用 {@link #evict}（须在持锁状态下），以便下次采集重建连接。</li>
 * </ul>
 *
 * <p><b>常驻连接 vs 临时连接</b>：常驻缓存连接仅供高频轻查询（分钟级采集、告警评估）使用；
 * 低频重查询（小时级容量扫描、天级参数快照）应使用 {@link #openEphemeral} 开临时连接、用完即关，
 * 不占实例锁。稳态下每实例仍只占目标库 1 条连接，小时/天级采集期间短暂升至 2 条。
 */
@Component
public class TargetConnectionCache {

    private static final Logger log = LoggerFactory.getLogger(TargetConnectionCache.class);

    /** 存活校验超时（秒）。 */
    private static final int VALIDATE_TIMEOUT_SECS = 2;

    /** 连接建立超时：3 秒。 */
    private static final int CONNECT_TIMEOUT_SECS = 3;

    /**
     * socketTimeout 作为兜底保护：30 秒。实际查询超时由各 Item 通过
     * Statement.setQueryTimeout() 控制，避免 heavy SQL 关闭 socket。
     */
    private static final int SOCKET_TIMEOUT_SECS = 30;

    /** borrow 失败后最多重试次数。 */
    private static final int MAX_BORROW_RETRIES = 2;

    /** 重试等待间隔（毫秒）。 */
    private static final long RETRY_INTERVAL_MS = 500;

    /** 定时健康检查间隔（毫秒）。 */
    private static final long HEALTH_CHECK_INTERVAL_MS = 30_000;

    private record CachedEntry(Connection connection, String cacheKey) {}

    private final ConcurrentHashMap<Long, CachedEntry> cache = new ConcurrentHashMap<>();

    /** 实例级互斥锁：借用/使用/驱逐连接期间必须持有，条目数 ≤ 实例数（≤1000），不主动清理。 */
    private final ConcurrentHashMap<Long, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();

    /**
     * 获取实例级互斥锁。调用方在 {@code lock()} 与 {@code unlock()} 之间完成
     * {@link #borrow} 及全部基于该连接的 JDBC 操作，避免采集与告警评估并发共用同一连接。
     */
    public ReentrantLock instanceLock(Long instanceId) {
        return instanceLocks.computeIfAbsent(instanceId, k -> new ReentrantLock());
    }

    /**
     * 获取或创建目标实例的 JDBC 连接。
     *
     * <ol>
     *   <li>缓存命中且 cacheKey 不变且 isValid 通过 → 直接返回。</li>
     *   <li>缓存过期 / cacheKey 变化 / 失活 → 关闭旧连接，带重试地新建后缓存并返回。</li>
     * </ol>
     *
     * @param instanceId 实例 ID（缓存主键）
     * @param target     连接参数（jdbcUrl / username / password / driverClass）
     * @return 可用的 JDBC 连接（不得由调用方 close）
     * @throws Exception 重试 {@value #MAX_BORROW_RETRIES} 次后仍无法建立连接
     */
    public Connection borrow(Long instanceId, TargetDataSource target) throws Exception {
        String newKey = cacheKey(target);
        CachedEntry existing = cache.get(instanceId);

        if (existing != null) {
            if (newKey.equals(existing.cacheKey())) {
                try {
                    if (existing.connection().isValid(VALIDATE_TIMEOUT_SECS)) {
                        return existing.connection();
                    }
                    log.debug("实例 {} 缓存连接已失活，重建", instanceId);
                } catch (Exception ignored) {
                    log.debug("实例 {} 缓存连接 isValid 异常，重建", instanceId);
                }
            } else {
                log.info("实例 {} 连接参数已变更（url/user/password），重建连接", instanceId);
            }
            closeQuietly(existing.connection());
            cache.remove(instanceId, existing);
        }

        // 带重试地新建连接
        Exception lastEx = null;
        for (int attempt = 1; attempt <= MAX_BORROW_RETRIES; attempt++) {
            try {
                Connection conn = openConnection(target);
                cache.put(instanceId, new CachedEntry(conn, newKey));
                if (attempt > 1) {
                    log.info("实例 {} 第 {} 次重试建连成功", instanceId, attempt);
                } else {
                    log.debug("实例 {} 新建缓存连接", instanceId);
                }
                return conn;
            } catch (Exception ex) {
                lastEx = ex;
                log.warn("实例 {} 建连失败（第 {}/{} 次）: {}", instanceId, attempt, MAX_BORROW_RETRIES, ex.getMessage());
                if (attempt < MAX_BORROW_RETRIES) {
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            }
        }
        throw lastEx;
    }

    /**
     * 开一条<b>临时连接</b>（带与 {@link #borrow} 相同的重试策略），不进缓存、不参与健康检查。
     *
     * <p>供小时级/天级等低频重查询使用：调用方<b>自行负责 close</b>（建议 try-with-resources），
     * 且无须持有 {@link #instanceLock}——临时连接与常驻连接彼此独立，互不干扰。
     *
     * @param instanceId 实例 ID（仅用于日志）
     * @param target     连接参数（jdbcUrl / username / password / driverClass）
     * @return 新建的 JDBC 连接，由调用方关闭
     * @throws Exception 重试 {@value #MAX_BORROW_RETRIES} 次后仍无法建立连接
     */
    public Connection openEphemeral(Long instanceId, TargetDataSource target) throws Exception {
        Exception lastEx = null;
        for (int attempt = 1; attempt <= MAX_BORROW_RETRIES; attempt++) {
            try {
                Connection conn = openConnection(target);
                log.debug("实例 {} 新建临时连接（低频采集）", instanceId);
                return conn;
            } catch (Exception ex) {
                lastEx = ex;
                log.warn("实例 {} 临时连接建连失败（第 {}/{} 次）: {}",
                        instanceId, attempt, MAX_BORROW_RETRIES, ex.getMessage());
                if (attempt < MAX_BORROW_RETRIES) {
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            }
        }
        throw lastEx;
    }

    /**
     * 驱逐并关闭指定实例的缓存连接。
     * 当采集过程中确认连接已损坏时调用，确保下次采集能重新建立。
     */
    public void evict(Long instanceId) {
        CachedEntry entry = cache.remove(instanceId);
        if (entry != null) {
            closeQuietly(entry.connection());
            log.debug("实例 {} 已驱逐缓存连接", instanceId);
        }
    }

    /**
     * 定时健康检查：每 {@value #HEALTH_CHECK_INTERVAL_MS}ms 遍历缓存中所有连接，
     * 主动 ping（isValid），失活则提前驱逐，避免下轮采集时才发现连接已死。
     *
     * <p>需在 Spring 上下文中启用 {@code @EnableScheduling}（CollectorApplication 已启用）。
     */
    @Scheduled(fixedDelay = HEALTH_CHECK_INTERVAL_MS, initialDelay = HEALTH_CHECK_INTERVAL_MS)
    public void healthCheck() {
        if (cache.isEmpty()) return;
        log.debug("开始连接健康检查，当前缓存连接数: {}", cache.size());
        int evicted = 0;
        for (Long instanceId : cache.keySet()) {
            ReentrantLock lock = instanceLock(instanceId);
            // 抢不到锁说明连接正在被采集/评估使用（即仍然存活），跳过本轮检查即可
            if (!lock.tryLock()) {
                continue;
            }
            try {
                CachedEntry entry = cache.get(instanceId);
                if (entry == null) continue;
                try {
                    if (!entry.connection().isValid(VALIDATE_TIMEOUT_SECS)) {
                        evict(instanceId);
                        evicted++;
                        log.info("健康检查：实例 {} 连接已失活，已驱逐", instanceId);
                    }
                } catch (SQLException e) {
                    evict(instanceId);
                    evicted++;
                    log.info("健康检查：实例 {} 连接异常（{}），已驱逐", instanceId, e.getMessage());
                }
            } finally {
                lock.unlock();
            }
        }
        if (evicted > 0) {
            log.info("健康检查完成，共驱逐 {} 条失活连接", evicted);
        }
    }

    /**
     * 应用关闭时释放全部缓存连接。
     * <p>逐实例带超时地获取互斥锁后再关闭：停机时采集/评估线程可能仍在使用连接，
     * 直接关闭会造成使用方报错。等待超时（连接使用方受 socketTimeout 约束、耗时有界）
     * 仍未获得锁则强制关闭——进程即将退出，此时倾向于释放资源而非等待。
     */
    @PreDestroy
    public void closeAll() {
        log.info("关闭全部目标库缓存连接（共 {} 条）", cache.size());
        for (Long instanceId : cache.keySet()) {
            ReentrantLock lock = instanceLock(instanceId);
            boolean locked = false;
            try {
                locked = lock.tryLock(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                CachedEntry entry = cache.remove(instanceId);
                if (entry != null) {
                    closeQuietly(entry.connection());
                }
            } finally {
                if (locked) {
                    lock.unlock();
                }
            }
        }
        cache.clear();
    }

    /** 当前缓存中的连接数（可用于监控/诊断）。 */
    public int size() {
        return cache.size();
    }

    // ---- 内部工具 ----

    private Connection openConnection(TargetDataSource target) throws Exception {
        if (target.getDriverClass() != null && !target.getDriverClass().isBlank()) {
            Class.forName(target.getDriverClass());
        }
        Properties props = buildProps(target);
        Connection conn = DriverManager.getConnection(target.getJdbcUrl(), props);
        conn.setReadOnly(true);
        conn.setAutoCommit(true);
        return conn;
    }

    /**
     * 按 JDBC URL 前缀构造驱动专属连接属性：
     * MySQL 的 connectTimeout / socketTimeout 单位为毫秒并需关闭 SSL 校验；
     * PostgreSQL 的同名属性单位为秒。传错单位会导致超时保护完全失效或立即超时。
     */
    private static Properties buildProps(TargetDataSource target) {
        Properties props = new Properties();
        props.setProperty("user", target.getUsername() == null ? "" : target.getUsername());
        props.setProperty("password", target.getPassword() == null ? "" : target.getPassword());
        String url = target.getJdbcUrl() == null ? "" : target.getJdbcUrl();
        if (url.startsWith("jdbc:postgresql:")) {
            props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECS));
            props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECS));
            props.setProperty("ApplicationName", "monitor-collector");
        } else {
            props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECS * 1000));
            props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECS * 1000));
            props.setProperty("useSSL", "false");
            props.setProperty("allowPublicKeyRetrieval", "true");
        }
        return props;
    }

    /**
     * 缓存键：jdbcUrl + "|" + username + "|" + password 的 SHA-256 摘要。
     * 密码只以摘要形式参与比较（不明文保存/打印），密码变更后 cacheKey 变化即驱逐旧连接重建，
     * 避免旧连接因仍然有效而长期沿用旧凭据。
     */
    private static String cacheKey(TargetDataSource target) {
        return (target.getJdbcUrl() == null ? "" : target.getJdbcUrl())
                + "|"
                + (target.getUsername() == null ? "" : target.getUsername())
                + "|"
                + sha256(target.getPassword() == null ? "" : target.getPassword());
    }

    private static String sha256(String input) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，理论不可达
            throw new IllegalStateException(e);
        }
    }

    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
    }
}
