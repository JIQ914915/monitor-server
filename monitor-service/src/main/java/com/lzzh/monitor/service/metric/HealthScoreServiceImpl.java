package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.HealthScoreVo;
import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadataService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 健康评分计算服务实现（实时概况页与 healthCalculateJobHandler 共用同一口径）。
 *
 * <h3>评分模型</h3>
 * <ul>
 *   <li>五维度各从 100 分起扣，按实际指标值触发扣分规则，不低于 0</li>
 *   <li>权重：可用性30% + 性能25% + 稳定性20% + 容量15% + 安全10%</li>
 *   <li>综合分 = 各维度分 × 权重，四舍五入取整</li>
 *   <li>若某维度所有输入指标均无新鲜数据，该维度计为 -1（不参与加权，其余维度等比放大权重）</li>
 *   <li>所有维度均无数据时返回 score=-1, level="no_data"</li>
 * </ul>
 *
 * <h3>维度指标集（按数据库类型分派）</h3>
 * <p>MySQL：
 * <ul>
 *   <li>可用性：mysql.availability（连接失败时由采集器写 0）、Uptime（刚重启）、Threads_connected、
 *       复制 IO/SQL 线程状态（复制断裂视为数据服务不完整）</li>
 *   <li>性能：连接使用率（Threads_connected / max_connections）、活跃线程占比（Threads_running / Threads_connected）、
 *       每分钟新增慢查询（mysql.delta.slow_queries）、Buffer Pool 命中率</li>
 *   <li>稳定性：最长事务时长、活跃事务数、锁等待/被阻塞会话、复制延迟（仅从库）</li>
 *   <li>容量：数据总量 mysql.capacity.data_size_bytes（小时级）</li>
 *   <li>安全：天级安全巡检（空密码/匿名/宽泛授权/SUPER 权限账号数）</li>
 * </ul>
 * <p>PostgreSQL（一期指标集内取对应口径）：
 * <ul>
 *   <li>可用性：pg.availability、pg.uptime、pg.conn.total</li>
 *   <li>性能：连接使用率 pg.conn.usage、活跃连接占比、shared_buffers 命中率、临时文件产生</li>
 *   <li>稳定性：最长事务/事务中空闲时长、锁等待/被阻塞会话、死锁、回滚占比、复制延迟（仅从库）</li>
 *   <li>容量：实例总容量 pg.capacity.total_size_bytes（小时级）</li>
 *   <li>安全配置：idle_in_transaction_session_timeout / statement_timeout 未设置（天级参数快照）</li>
 * </ul>
 */
@Service
public class HealthScoreServiceImpl implements HealthScoreService {

    // ---- 1m 指标 ----
    private static final Set<String> METRICS_1M = Set.of(
            "mysql.availability",
            "mysql.status.Uptime",
            "mysql.status.Threads_connected",
            "mysql.status.Threads_running",
            "mysql.delta.slow_queries",
            "mysql.innodb.buffer_pool_hit_rate",
            "mysql.innodb.trx_active",
            "mysql.innodb.trx_max_seconds",
            "mysql.innodb.lock_waits",
            "mysql.innodb.blocked_sessions",
            "mysql.replication.is_replica",
            "mysql.replication.io_running",
            "mysql.replication.sql_running",
            "mysql.replication.seconds_behind"
    );

    // ---- 1h 指标（容量，小时级采集）----
    private static final Set<String> METRICS_1H = Set.of(
            "mysql.capacity.data_size_bytes"
    );

    // ---- 1d 指标（max_connections 参数 + 安全巡检，天级采集）----
    private static final Set<String> METRICS_1D = Set.of(
            "mysql.var.max_connections",
            "mysql.security.empty_password_count",
            "mysql.security.any_host_account_count",
            "mysql.security.anonymous_user_count",
            "mysql.security.super_priv_count"
    );

    // ---- PostgreSQL 指标集 ----
    private static final Set<String> PG_METRICS_1M = Set.of(
            "pg.availability",
            "pg.uptime",
            "pg.conn.total",
            "pg.conn.active",
            "pg.conn.usage",
            "pg.conn.idle_in_trx",
            "pg.cache.hit_rate",
            "pg.delta.temp_files",
            "pg.trx.active",
            "pg.trx.max_seconds",
            "pg.trx.idle_in_trx_max_seconds",
            "pg.locks.waiting",
            "pg.blocked_sessions",
            "pg.delta.deadlocks",
            "pg.rate.xact_commit",
            "pg.rate.xact_rollback",
            "pg.repl.is_replica",
            "pg.repl.lag_seconds"
    );

    private static final Set<String> PG_METRICS_1H = Set.of(
            "pg.capacity.total_size_bytes"
    );

    private static final Set<String> PG_METRICS_1D = Set.of(
            "pg.setting.idle_in_trx_timeout_ms",
            "pg.setting.statement_timeout_ms"
    );

    private static final Set<String> SQLSERVER_METRICS_1M = Set.of(
            "sqlserver.availability","sqlserver.uptime","sqlserver.scheduler.runnable_tasks",
            "sqlserver.memory.grants_pending","sqlserver.blocked_sessions","sqlserver.deadlocks_per_sec",
            "sqlserver.storage.log_used_percent","sqlserver.io.read_latency_ms","sqlserver.io.write_latency_ms",
            "sqlserver.ag.disconnected_replicas","sqlserver.ag.unhealthy_databases",
            "sqlserver.ag.suspended_databases","sqlserver.ag.max_send_seconds","sqlserver.ag.max_redo_seconds");
    private static final Set<String> SQLSERVER_METRICS_1H = Set.of(
            "sqlserver.backup.max_full_age_hours","sqlserver.backup.uncovered_database_count",
            "sqlserver.backup.log_missing_database_count");

    /** max_connections 无数据时的连接使用率基准。 */
    private static final double DEFAULT_MAX_CONNECTIONS = 500;

    private static final double GB = 1024L * 1024 * 1024;

    // ---- 维度权重 ----
    private static final int W_AVAILABILITY = 30;
    private static final int W_PERFORMANCE  = 25;
    private static final int W_STABILITY    = 20;
    private static final int W_CAPACITY     = 15;
    private static final int W_SECURITY     = 10;

    @Resource
    private TsMetricLatestDao latestDao;
    @Resource
    private InstanceRuntimeMetadataService runtimeMetadataService;
    private final HealthScorePolicyRegistry policyRegistry = new HealthScorePolicyRegistry(List.of(
            new MySqlHealthScorePolicy(),
            new PostgreSqlHealthScorePolicy(),
            new SqlServerHealthScorePolicy()));

    @Override
    public HealthScoreVo calculate(Long instanceId) {
        return policyRegistry.find(resolveDbType(instanceId)).calculate(instanceId);
    }

    private DbType resolveDbType(Long instanceId) {
        String code = runtimeMetadataService.getRequired(instanceId).dbTypeCode();
        DbType type = DbType.of(code);
        if (type == null) {
            throw new UnsupportedOperationException("暂不支持健康评分的数据库类型: " + code);
        }
        return type;
    }

    private final class MySqlHealthScorePolicy implements HealthScorePolicy {
        @Override
        public DbType supportedType() {
            return DbType.MYSQL;
        }

        @Override
        public HealthScoreVo calculate(Long instanceId) {
            return calculateMySql(instanceId);
        }
    }

    private final class PostgreSqlHealthScorePolicy implements HealthScorePolicy {
        @Override
        public DbType supportedType() {
            return DbType.POSTGRESQL;
        }

        @Override
        public HealthScoreVo calculate(Long instanceId) {
            return calculatePg(instanceId);
        }
    }
    private final class SqlServerHealthScorePolicy implements HealthScorePolicy {
        @Override public DbType supportedType() { return DbType.SQLSERVER; }
        @Override public HealthScoreVo calculate(Long instanceId) { return calculateSqlServer(instanceId); }
    }

    private HealthScoreVo calculateMySql(Long instanceId) {
        Map<String, Double> m1 = latestDao.latestFrom1m(instanceId, METRICS_1M);
        Map<String, Double> m1h = latestDao.latestFrom1h(instanceId, METRICS_1H);
        Map<String, Double> m1d = latestDao.latestFrom1d(instanceId, METRICS_1D);

        List<HealthScoreVo.Deduction> deductions = new ArrayList<>();

        int scoreAvail    = calcAvailability(m1, deductions);
        int scorePerf     = calcPerformance(m1, m1d, deductions);
        int scoreStability= calcStability(m1, deductions);
        int scoreCapacity = calcCapacity(m1h, deductions);
        int scoreSecurity = calcSecurity(m1d, deductions);

        return assemble(instanceId, scoreAvail, scorePerf, scoreStability, scoreCapacity, scoreSecurity, deductions);
    }

    private HealthScoreVo calculatePg(Long instanceId) {
        Map<String, Double> m1 = latestDao.latestFrom1m(instanceId, PG_METRICS_1M);
        Map<String, Double> m1h = latestDao.latestFrom1h(instanceId, PG_METRICS_1H);
        Map<String, Double> m1d = latestDao.latestFrom1d(instanceId, PG_METRICS_1D);

        List<HealthScoreVo.Deduction> deductions = new ArrayList<>();

        int scoreAvail    = calcPgAvailability(m1, deductions);
        int scorePerf     = calcPgPerformance(m1, deductions);
        int scoreStability= calcPgStability(m1, deductions);
        int scoreCapacity = calcPgCapacity(m1h, deductions);
        int scoreSecurity = calcPgSecurity(m1d, deductions);

        return assemble(instanceId, scoreAvail, scorePerf, scoreStability, scoreCapacity, scoreSecurity, deductions);
    }

    private HealthScoreVo calculateSqlServer(Long instanceId) {
        Map<String,Double> m=latestDao.latestFrom1m(instanceId,SQLSERVER_METRICS_1M);
        Map<String,Double> h=latestDao.latestFrom1h(instanceId,SQLSERVER_METRICS_1H);
        List<HealthScoreVo.Deduction> out=new ArrayList<>();
        if(m.isEmpty()&&h.isEmpty()) return assemble(instanceId,-1,-1,-1,-1,-1,out);
        int availability=100,performance=100,stability=100,capacity=100;
        Double available=m.get("sqlserver.availability");
        if(available!=null&&available<1){availability-=80;out.add(deduct("availability","SQL Server 实例当前无法连接",80,"0"));}
        Double disconnected=m.get("sqlserver.ag.disconnected_replicas");
        if(disconnected!=null&&disconnected>0){availability-=30;out.add(deduct("availability","Always On 存在断连副本，请检查端点、网络和集群状态",30,fmt0(disconnected)));}
        Double runnable=m.get("sqlserver.scheduler.runnable_tasks");
        if(runnable!=null&&runnable>=4){performance-=25;out.add(deduct("performance","CPU 调度持续排队，请关联主机 CPU、Top SQL 和等待",25,fmt0(runnable)));}
        Double grants=m.get("sqlserver.memory.grants_pending");
        if(grants!=null&&grants>0){performance-=20;out.add(deduct("performance","查询正在等待内存授权",20,fmt0(grants)));}
        Double writeLatency=m.get("sqlserver.io.write_latency_ms");
        if(writeLatency!=null&&writeLatency>=20){performance-=15;out.add(deduct("performance","数据库文件写入延迟偏高",15,fmt1(writeLatency)+"ms"));}
        Double blocked=m.get("sqlserver.blocked_sessions");
        if(blocked!=null&&blocked>0){stability-=20;out.add(deduct("stability","存在被阻塞会话，可下钻查看阻塞链",20,fmt0(blocked)));}
        Double deadlocks=m.get("sqlserver.deadlocks_per_sec");
        if(deadlocks!=null&&deadlocks>0){stability-=25;out.add(deduct("stability","检测到死锁，需复盘脱敏死锁图和加锁顺序",25,fmt1(deadlocks)));}
        Double unhealthy=m.get("sqlserver.ag.unhealthy_databases");
        if(unhealthy!=null&&unhealthy>0){stability-=25;out.add(deduct("stability","Always On 数据库同步不健康",25,fmt0(unhealthy)));}
        Double logUsage=m.get("sqlserver.storage.log_used_percent");
        if(logUsage!=null&&logUsage>=85){capacity-=30;out.add(deduct("capacity","事务日志使用率偏高，需结合复用等待、日志备份和长事务判断",30,fmt1(logUsage)+"%"));}
        Double uncovered=h.get("sqlserver.backup.uncovered_database_count");
        if(uncovered!=null&&uncovered>0){stability-=35;out.add(deduct("stability","存在未纳入完整备份的用户数据库；备份记录也不等于可恢复",35,fmt0(uncovered)));}
        Double fullAge=h.get("sqlserver.backup.max_full_age_hours");
        if(fullAge!=null&&fullAge>24){stability-=20;out.add(deduct("stability","用户数据库完整备份已超过 24 小时",20,fmt0(fullAge)+"h"));}
        return assemble(instanceId,Math.max(0,availability),Math.max(0,performance),
                Math.max(0,stability),Math.max(0,capacity),-1,out);
    }

    private HealthScoreVo assemble(Long instanceId, int scoreAvail, int scorePerf, int scoreStability,
                                   int scoreCapacity, int scoreSecurity,
                                   List<HealthScoreVo.Deduction> deductions) {
        // 加权合成（跳过无数据维度）
        int overall = weightedAverage(
                scoreAvail,    W_AVAILABILITY,
                scorePerf,     W_PERFORMANCE,
                scoreStability,W_STABILITY,
                scoreCapacity, W_CAPACITY,
                scoreSecurity, W_SECURITY
        );

        HealthScoreVo vo = new HealthScoreVo();
        vo.setInstanceId(instanceId);
        vo.setScore(overall);
        vo.setLevel(toLevel(overall));
        vo.setDimensions(buildDimensions(scoreAvail, scorePerf, scoreStability, scoreCapacity, scoreSecurity));
        vo.setDeductions(deductions);
        return vo;
    }

    // ──────────────────────────── 可用性维度（30%）────────────────────────────

    private int calcAvailability(Map<String, Double> m, List<HealthScoreVo.Deduction> out) {
        Double avail = m.get("mysql.availability");
        Double uptime = m.get("mysql.status.Uptime");
        Double connected = m.get("mysql.status.Threads_connected");
        if (avail == null && uptime == null && connected == null) {
            return -1;  // 无数据
        }
        int score = 100;
        // 连接失败时采集器显式写 availability=0（有 Uptime 说明连接正常，不重复扣）
        if (avail != null && avail < 1.0 && uptime == null) {
            score -= 80;
            out.add(deduct("availability", "实例连接不可用（availability=0）", 80, "0"));
        }
        // 刚重启（运行时长 < 60 秒）
        if (uptime != null && uptime < 60) {
            score -= 30;
            out.add(deduct("availability", "实例刚重启（Uptime < 60s，当前：" + fmt0(uptime) + "s）", 30, fmt0(uptime) + "s"));
        }
        // 无任何连接（连正常业务连接都没有，可能拒绝服务）
        if (connected != null && connected <= 0) {
            score -= 40;
            out.add(deduct("availability", "当前连接数为 0，可能无法接受连接", 40, "0"));
        }
        // 复制线程断裂（仅从库有意义）：数据服务不完整，属于硬故障
        Double isReplica = m.get("mysql.replication.is_replica");
        if (isReplica != null && isReplica >= 1) {
            Double ioRunning = m.get("mysql.replication.io_running");
            Double sqlRunning = m.get("mysql.replication.sql_running");
            if (ioRunning != null && ioRunning < 1.0) {
                score -= 15;
                out.add(deduct("availability", "复制 IO 线程未运行，从库数据不再更新", 15, "0"));
            }
            if (sqlRunning != null && sqlRunning < 1.0) {
                score -= 10;
                out.add(deduct("availability", "复制 SQL 线程未运行，中继日志未回放", 10, "0"));
            }
        }
        return Math.max(0, score);
    }

    // ──────────────────────────── 性能维度（25%）────────────────────────────

    private int calcPerformance(Map<String, Double> m, Map<String, Double> m1d,
                                List<HealthScoreVo.Deduction> out) {
        Double connected = m.get("mysql.status.Threads_connected");
        Double running = m.get("mysql.status.Threads_running");
        Double slowDelta = m.get("mysql.delta.slow_queries");
        Double hitRate = m.get("mysql.innodb.buffer_pool_hit_rate");
        if (connected == null && running == null && slowDelta == null && hitRate == null) {
            return -1;
        }
        int score = 100;
        // 连接使用率 = Threads_connected / max_connections（1d 参数快照，无则用基准 500）
        Double maxConn = m1d.get("mysql.var.max_connections");
        double base = (maxConn != null && maxConn > 0) ? maxConn : DEFAULT_MAX_CONNECTIONS;
        if (connected != null) {
            double usage = connected / base;
            String cur = fmt1(usage * 100) + "%";
            if (usage >= 0.99) {
                score -= 45;
                out.add(deduct("performance", "连接使用率 ≥ 99%，接近打满（当前：" + cur + "）", 45, cur));
            } else if (usage >= 0.90) {
                score -= 35;
                out.add(deduct("performance", "连接使用率 ≥ 90%（当前：" + cur + "）", 35, cur));
            } else if (usage >= 0.80) {
                score -= 20;
                out.add(deduct("performance", "连接使用率 ≥ 80%（当前：" + cur + "）", 20, cur));
            } else if (usage >= 0.50) {
                score -= 10;
                out.add(deduct("performance", "连接使用率 ≥ 50%（当前：" + cur + "）", 10, cur));
            }
        }
        // 活跃线程占比 = Threads_running / Threads_connected
        if (connected != null && connected > 0 && running != null) {
            double ratio = running / connected;
            String cur = fmt1(ratio * 100) + "%";
            if (ratio >= 0.80) {
                score -= 35;
                out.add(deduct("performance", "活跃线程占比 ≥ 80%，负载偏高（当前：" + cur + "）", 35, cur));
            } else if (ratio >= 0.50) {
                score -= 20;
                out.add(deduct("performance", "活跃线程占比 ≥ 50%（当前：" + cur + "）", 20, cur));
            } else if (ratio >= 0.20) {
                score -= 10;
                out.add(deduct("performance", "活跃线程占比 ≥ 20%（当前：" + cur + "）", 10, cur));
            }
        }
        // 每分钟新增慢查询（周期增量，用户体感最直接的性能信号）
        if (slowDelta != null) {
            String cur = fmt0(slowDelta);
            if (slowDelta > 50) {
                score -= 20;
                out.add(deduct("performance", "本分钟新增慢查询 > 50 条（当前：" + cur + "）", 20, cur));
            } else if (slowDelta > 10) {
                score -= 10;
                out.add(deduct("performance", "本分钟新增慢查询 > 10 条（当前：" + cur + "）", 10, cur));
            } else if (slowDelta > 0) {
                score -= 3;
                out.add(deduct("performance", "本分钟存在慢查询（当前：" + cur + " 条）", 3, cur));
            }
        }
        // Buffer Pool 读命中率
        if (hitRate != null) {
            String cur = fmt1(hitRate) + "%";
            if (hitRate < 90) {
                score -= 20;
                out.add(deduct("performance", "Buffer Pool 命中率 < 90%（当前：" + cur + "），热数据超出内存", 20, cur));
            } else if (hitRate < 95) {
                score -= 8;
                out.add(deduct("performance", "Buffer Pool 命中率 < 95%（当前：" + cur + "）", 8, cur));
            }
        }
        return Math.max(0, score);
    }

    // ──────────────────────────── 稳定性维度（20%）────────────────────────────

    private int calcStability(Map<String, Double> m, List<HealthScoreVo.Deduction> out) {
        Double trxMaxSec = m.get("mysql.innodb.trx_max_seconds");
        Double trxActive = m.get("mysql.innodb.trx_active");
        Double lockWaits = m.get("mysql.innodb.lock_waits");
        Double blockedSess = m.get("mysql.innodb.blocked_sessions");
        Double isReplica = m.get("mysql.replication.is_replica");
        if (trxMaxSec == null && trxActive == null && lockWaits == null
                && blockedSess == null && isReplica == null) {
            return -1;
        }
        int score = 100;
        // 锁等待：业务偶发卡死的头号原因
        if (lockWaits != null) {
            String cur = fmt0(lockWaits);
            if (lockWaits > 5) {
                score -= 20;
                out.add(deduct("stability", "锁等待数 > 5（当前：" + cur + "），存在明显锁冲突", 20, cur));
            } else if (lockWaits > 0) {
                score -= 8;
                out.add(deduct("stability", "存在锁等待（当前：" + cur + "）", 8, cur));
            }
        }
        if (blockedSess != null && blockedSess > 0) {
            score -= 10;
            out.add(deduct("stability", "存在被阻塞会话（当前：" + fmt0(blockedSess) + "）", 10, fmt0(blockedSess)));
        }
        // 最长事务时长
        if (trxMaxSec != null) {
            String cur = fmt0(trxMaxSec) + "s";
            if (trxMaxSec >= 3600) {
                score -= 45;
                out.add(deduct("stability", "存在运行超 1 小时的长事务（当前最长：" + cur + "）", 45, cur));
            } else if (trxMaxSec >= 300) {
                score -= 25;
                out.add(deduct("stability", "存在运行超 5 分钟的长事务（当前最长：" + cur + "）", 25, cur));
            } else if (trxMaxSec >= 30) {
                score -= 10;
                out.add(deduct("stability", "存在运行超 30 秒的事务（当前最长：" + cur + "）", 10, cur));
            }
        }
        // 活跃事务数
        if (trxActive != null) {
            String cur = fmt0(trxActive);
            if (trxActive >= 500) {
                score -= 40;
                out.add(deduct("stability", "活跃事务数 ≥ 500（当前：" + cur + "）", 40, cur));
            } else if (trxActive >= 100) {
                score -= 25;
                out.add(deduct("stability", "活跃事务数 ≥ 100（当前：" + cur + "）", 25, cur));
            } else if (trxActive >= 10) {
                score -= 10;
                out.add(deduct("stability", "活跃事务数 ≥ 10（当前：" + cur + "）", 10, cur));
            }
        }
        // 复制延迟（仅从库有效）
        if (isReplica != null && isReplica >= 1) {
            Double lag = m.get("mysql.replication.seconds_behind");
            if (lag != null) {
                String cur = fmt0(lag) + "s";
                if (lag >= 600) {
                    score -= 45;
                    out.add(deduct("stability", "复制延迟 ≥ 600 秒（当前：" + cur + "）", 45, cur));
                } else if (lag >= 60) {
                    score -= 35;
                    out.add(deduct("stability", "复制延迟 ≥ 60 秒（当前：" + cur + "）", 35, cur));
                } else if (lag >= 10) {
                    score -= 20;
                    out.add(deduct("stability", "复制延迟 ≥ 10 秒（当前：" + cur + "）", 20, cur));
                } else if (lag >= 1) {
                    score -= 10;
                    out.add(deduct("stability", "存在复制延迟（当前：" + cur + "）", 10, cur));
                }
            }
        }
        return Math.max(0, score);
    }

    // ──────────────────────────── 容量维度（15%）────────────────────────────

    private int calcCapacity(Map<String, Double> m1h, List<HealthScoreVo.Deduction> out) {
        Double dataSize = m1h.get("mysql.capacity.data_size_bytes");
        if (dataSize == null) {
            return -1;  // 小时级容量数据未到：不参与加权
        }
        int score = 100;
        String cur = fmt1(dataSize / GB) + "GB";
        if (dataSize >= 500 * GB) {
            score -= 50;
            out.add(deduct("capacity", "数据总量 ≥ 500GB（当前：" + cur + "），建议关注容量规划", 50, cur));
        } else if (dataSize >= 200 * GB) {
            score -= 30;
            out.add(deduct("capacity", "数据总量 ≥ 200GB（当前：" + cur + "）", 30, cur));
        } else if (dataSize >= 50 * GB) {
            score -= 15;
            out.add(deduct("capacity", "数据总量 ≥ 50GB（当前：" + cur + "）", 15, cur));
        }
        return Math.max(0, score);
    }

    // ──────────────────────────── 安全配置维度（10%）────────────────────────────

    private int calcSecurity(Map<String, Double> m1d, List<HealthScoreVo.Deduction> out) {
        Double emptyPwd  = m1d.get("mysql.security.empty_password_count");
        Double anyHost   = m1d.get("mysql.security.any_host_account_count");
        Double anonUser  = m1d.get("mysql.security.anonymous_user_count");
        Double superPriv = m1d.get("mysql.security.super_priv_count");
        if (emptyPwd == null && anyHost == null && anonUser == null && superPriv == null) {
            return -1;
        }
        int score = 100;
        if (emptyPwd != null && emptyPwd > 0) {
            score -= 40;
            out.add(deduct("security", "存在空密码账号（" + fmt0(emptyPwd) + " 个），高风险", 40, fmt0(emptyPwd)));
        }
        if (anonUser != null && anonUser > 0) {
            score -= 30;
            out.add(deduct("security", "存在匿名账号（" + fmt0(anonUser) + " 个），应立即清理", 30, fmt0(anonUser)));
        }
        if (anyHost != null) {
            if (anyHost > 5) {
                score -= 15;
                out.add(deduct("security", "宽泛授权账号（Host='%'）超过 5 个（当前：" + fmt0(anyHost) + "）", 15, fmt0(anyHost)));
            } else if (anyHost > 0) {
                score -= 8;
                out.add(deduct("security", "存在宽泛授权账号（Host='%'，当前：" + fmt0(anyHost) + " 个）", 8, fmt0(anyHost)));
            }
        }
        if (superPriv != null && superPriv > 3) {
            score -= 10;
            out.add(deduct("security", "SUPER 权限账号超过 3 个（当前：" + fmt0(superPriv) + "），建议最小化", 10, fmt0(superPriv)));
        }
        return Math.max(0, score);
    }

    // ════════════════════════════ PostgreSQL 维度 ════════════════════════════

    private int calcPgAvailability(Map<String, Double> m, List<HealthScoreVo.Deduction> out) {
        Double avail = m.get("pg.availability");
        Double uptime = m.get("pg.uptime");
        Double connTotal = m.get("pg.conn.total");
        if (avail == null && uptime == null && connTotal == null) {
            return -1;
        }
        int score = 100;
        if (avail != null && avail < 1.0 && uptime == null) {
            score -= 80;
            out.add(deduct("availability", "实例连接不可用（availability=0）", 80, "0"));
        }
        if (uptime != null && uptime < 60) {
            score -= 30;
            out.add(deduct("availability", "实例刚重启（Uptime < 60s，当前：" + fmt0(uptime) + "s）", 30, fmt0(uptime) + "s"));
        }
        if (connTotal != null && connTotal <= 0) {
            score -= 40;
            out.add(deduct("availability", "当前连接数为 0，可能无法接受连接", 40, "0"));
        }
        return Math.max(0, score);
    }

    private int calcPgPerformance(Map<String, Double> m, List<HealthScoreVo.Deduction> out) {
        Double usage = m.get("pg.conn.usage");      // 已是百分比
        Double connTotal = m.get("pg.conn.total");
        Double connActive = m.get("pg.conn.active");
        Double hitRate = m.get("pg.cache.hit_rate"); // 已是百分比
        Double tempFiles = m.get("pg.delta.temp_files");
        if (usage == null && connTotal == null && hitRate == null && tempFiles == null) {
            return -1;
        }
        int score = 100;
        // 连接使用率（占 max_connections 百分比）
        if (usage != null) {
            String cur = fmt1(usage) + "%";
            if (usage >= 99) {
                score -= 45;
                out.add(deduct("performance", "连接使用率 ≥ 99%，接近打满（当前：" + cur + "）", 45, cur));
            } else if (usage >= 90) {
                score -= 35;
                out.add(deduct("performance", "连接使用率 ≥ 90%（当前：" + cur + "）", 35, cur));
            } else if (usage >= 80) {
                score -= 20;
                out.add(deduct("performance", "连接使用率 ≥ 80%（当前：" + cur + "）", 20, cur));
            } else if (usage >= 50) {
                score -= 10;
                out.add(deduct("performance", "连接使用率 ≥ 50%（当前：" + cur + "）", 10, cur));
            }
        }
        // 活跃连接占比（PG 每连接一个进程，活跃占比高 = CPU 压力大）
        if (connTotal != null && connTotal > 0 && connActive != null) {
            double ratio = connActive / connTotal;
            String cur = fmt1(ratio * 100) + "%";
            if (ratio >= 0.80) {
                score -= 35;
                out.add(deduct("performance", "活跃连接占比 ≥ 80%，负载偏高（当前：" + cur + "）", 35, cur));
            } else if (ratio >= 0.50) {
                score -= 20;
                out.add(deduct("performance", "活跃连接占比 ≥ 50%（当前：" + cur + "）", 20, cur));
            }
        }
        // shared_buffers 命中率
        if (hitRate != null) {
            String cur = fmt1(hitRate) + "%";
            if (hitRate < 90) {
                score -= 20;
                out.add(deduct("performance", "缓存命中率 < 90%（当前：" + cur + "），热数据超出内存", 20, cur));
            } else if (hitRate < 95) {
                score -= 8;
                out.add(deduct("performance", "缓存命中率 < 95%（当前：" + cur + "）", 8, cur));
            }
        }
        // 临时文件产生（排序/哈希落盘，反映 work_mem 不足或大查询）
        if (tempFiles != null && tempFiles > 0) {
            String cur = fmt0(tempFiles);
            if (tempFiles > 60) {
                score -= 15;
                out.add(deduct("performance", "本分钟产生临时文件 > 60 个（当前：" + cur + "），存在大查询或 work_mem 偏小", 15, cur));
            } else {
                score -= 5;
                out.add(deduct("performance", "本分钟产生临时文件（当前：" + cur + " 个）", 5, cur));
            }
        }
        return Math.max(0, score);
    }

    private int calcPgStability(Map<String, Double> m, List<HealthScoreVo.Deduction> out) {
        Double trxMaxSec = m.get("pg.trx.max_seconds");
        Double idleInTrxSec = m.get("pg.trx.idle_in_trx_max_seconds");
        Double trxActive = m.get("pg.trx.active");
        Double lockWaits = m.get("pg.locks.waiting");
        Double blockedSess = m.get("pg.blocked_sessions");
        Double deadlocks = m.get("pg.delta.deadlocks");
        Double isReplica = m.get("pg.repl.is_replica");
        if (trxMaxSec == null && idleInTrxSec == null && lockWaits == null
                && blockedSess == null && deadlocks == null && isReplica == null) {
            return -1;
        }
        int score = 100;
        if (lockWaits != null) {
            String cur = fmt0(lockWaits);
            if (lockWaits > 5) {
                score -= 20;
                out.add(deduct("stability", "等待中锁请求 > 5（当前：" + cur + "），存在明显锁冲突", 20, cur));
            } else if (lockWaits > 0) {
                score -= 8;
                out.add(deduct("stability", "存在锁等待（当前：" + cur + "）", 8, cur));
            }
        }
        if (blockedSess != null && blockedSess > 0) {
            score -= 10;
            out.add(deduct("stability", "存在被阻塞会话（当前：" + fmt0(blockedSess) + "）", 10, fmt0(blockedSess)));
        }
        // 最长事务：PG 中长事务额外阻碍 VACUUM 清理，危害高于 MySQL 同阈值
        if (trxMaxSec != null) {
            String cur = fmt0(trxMaxSec) + "s";
            if (trxMaxSec >= 3600) {
                score -= 45;
                out.add(deduct("stability", "存在运行超 1 小时的长事务（当前最长：" + cur + "），将阻碍 VACUUM 引发表膨胀", 45, cur));
            } else if (trxMaxSec >= 300) {
                score -= 25;
                out.add(deduct("stability", "存在运行超 5 分钟的长事务（当前最长：" + cur + "）", 25, cur));
            } else if (trxMaxSec >= 30) {
                score -= 10;
                out.add(deduct("stability", "存在运行超 30 秒的事务（当前最长：" + cur + "）", 10, cur));
            }
        }
        // 事务中空闲：拿着事务不提交，同样阻碍 VACUUM 且往往持有锁
        if (idleInTrxSec != null && idleInTrxSec >= 300) {
            String cur = fmt0(idleInTrxSec) + "s";
            score -= 20;
            out.add(deduct("stability", "存在事务中空闲超 5 分钟的连接（当前最长：" + cur + "），疑似应用忘记提交", 20, cur));
        }
        if (trxActive != null && trxActive >= 100) {
            String cur = fmt0(trxActive);
            score -= 20;
            out.add(deduct("stability", "活跃事务数 ≥ 100（当前：" + cur + "）", 20, cur));
        }
        // 本分钟新增死锁
        if (deadlocks != null && deadlocks > 0) {
            String cur = fmt0(deadlocks);
            score -= 25;
            out.add(deduct("stability", "本分钟发生死锁（" + cur + " 次），业务事务被自动回滚", 25, cur));
        }
        // 回滚占比（应用报错的直接信号）
        Double commit = m.get("pg.rate.xact_commit");
        Double rollback = m.get("pg.rate.xact_rollback");
        if (commit != null && rollback != null && (commit + rollback) >= 1) {
            double ratio = rollback / (commit + rollback);
            if (ratio >= 0.25) {
                String cur = fmt1(ratio * 100) + "%";
                score -= 15;
                out.add(deduct("stability", "事务回滚占比 ≥ 25%（当前：" + cur + "），应用可能在批量报错", 15, cur));
            }
        }
        // 复制延迟（仅从库有效）
        if (isReplica != null && isReplica >= 1) {
            Double lag = m.get("pg.repl.lag_seconds");
            if (lag != null) {
                String cur = fmt0(lag) + "s";
                if (lag >= 600) {
                    score -= 45;
                    out.add(deduct("stability", "复制回放延迟 ≥ 600 秒（当前：" + cur + "）", 45, cur));
                } else if (lag >= 60) {
                    score -= 35;
                    out.add(deduct("stability", "复制回放延迟 ≥ 60 秒（当前：" + cur + "）", 35, cur));
                } else if (lag >= 10) {
                    score -= 20;
                    out.add(deduct("stability", "复制回放延迟 ≥ 10 秒（当前：" + cur + "）", 20, cur));
                }
            }
        }
        return Math.max(0, score);
    }

    private int calcPgCapacity(Map<String, Double> m1h, List<HealthScoreVo.Deduction> out) {
        Double totalSize = m1h.get("pg.capacity.total_size_bytes");
        if (totalSize == null) {
            return -1;
        }
        int score = 100;
        String cur = fmt1(totalSize / GB) + "GB";
        if (totalSize >= 500 * GB) {
            score -= 50;
            out.add(deduct("capacity", "实例总容量 ≥ 500GB（当前：" + cur + "），建议关注容量规划", 50, cur));
        } else if (totalSize >= 200 * GB) {
            score -= 30;
            out.add(deduct("capacity", "实例总容量 ≥ 200GB（当前：" + cur + "）", 30, cur));
        } else if (totalSize >= 50 * GB) {
            score -= 15;
            out.add(deduct("capacity", "实例总容量 ≥ 50GB（当前：" + cur + "）", 15, cur));
        }
        return Math.max(0, score);
    }

    private int calcPgSecurity(Map<String, Double> m1d, List<HealthScoreVo.Deduction> out) {
        Double idleTimeout = m1d.get("pg.setting.idle_in_trx_timeout_ms");
        Double stmtTimeout = m1d.get("pg.setting.statement_timeout_ms");
        if (idleTimeout == null && stmtTimeout == null) {
            return -1;
        }
        int score = 100;
        if (idleTimeout != null && idleTimeout <= 0) {
            score -= 20;
            out.add(deduct("security", "idle_in_transaction_session_timeout 未设置，事务中空闲连接不会被自动终止", 20, "0"));
        }
        if (stmtTimeout != null && stmtTimeout <= 0) {
            score -= 10;
            out.add(deduct("security", "statement_timeout 未设置，失控查询不会被自动取消", 10, "0"));
        }
        return Math.max(0, score);
    }

    // ──────────────────────────── 工具方法 ────────────────────────────

    /**
     * 加权平均（跳过 score=-1 的维度，其余权重等比放大）。
     * 若全部维度均为 -1，返回 -1。
     */
    private static int weightedAverage(
            int sA, int wA, int sP, int wP,
            int sSt, int wSt, int sCap, int wCap,
            int sSec, int wSec) {
        double sumW = 0, sumS = 0;
        if (sA   >= 0) { sumW += wA;   sumS += (double) sA   * wA;   }
        if (sP   >= 0) { sumW += wP;   sumS += (double) sP   * wP;   }
        if (sSt  >= 0) { sumW += wSt;  sumS += (double) sSt  * wSt;  }
        if (sCap >= 0) { sumW += wCap; sumS += (double) sCap * wCap; }
        if (sSec >= 0) { sumW += wSec; sumS += (double) sSec * wSec; }
        if (sumW == 0) {
            return -1;  // 无数据
        }
        return (int) Math.round(sumS / sumW);
    }

    private static String toLevel(int score) {
        if (score < 0)  return "no_data";
        if (score >= 90) return "excellent";
        if (score >= 75) return "good";
        if (score >= 60) return "warning";
        return "critical";
    }

    private static List<HealthScoreVo.DimensionScore> buildDimensions(
            int sA, int sP, int sSt, int sCap, int sSec) {
        List<HealthScoreVo.DimensionScore> list = new ArrayList<>(5);
        list.add(dim("availability", "可用性",   sA,   W_AVAILABILITY));
        list.add(dim("performance",  "性能",     sP,   W_PERFORMANCE));
        list.add(dim("stability",    "稳定性",   sSt,  W_STABILITY));
        list.add(dim("capacity",     "容量",     sCap, W_CAPACITY));
        list.add(dim("security",     "安全配置", sSec, W_SECURITY));
        return list;
    }

    private static HealthScoreVo.DimensionScore dim(String code, String label, int score, int weight) {
        HealthScoreVo.DimensionScore d = new HealthScoreVo.DimensionScore();
        d.setDimension(code);
        d.setLabel(label);
        d.setScore(score);
        d.setWeight(weight);
        return d;
    }

    private static HealthScoreVo.Deduction deduct(String dim, String msg, int pts, String val) {
        HealthScoreVo.Deduction d = new HealthScoreVo.Deduction();
        d.setDimension(dim);
        d.setMessage(msg);
        d.setPoints(pts);
        d.setCurrentValue(val);
        return d;
    }

    private static String fmt1(double v) {
        return String.format("%.1f", v);
    }

    private static String fmt0(double v) {
        return String.format("%.0f", v);
    }
}
