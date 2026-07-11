package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.ParamAdviceVo;
import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import com.lzzh.monitor.dao.ts.TsParamQueryDao;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 参数调优建议实现（§15.4.6，规则化体检）。
 *
 * <p>思路：把 DBA 的经典体检口径固化为规则——"配置参数当前值 + 运行指标表现"联合判断，
 * 输出观察依据与调整建议。产品定位约束：<b>只出建议不出手</b>，所有调整须人工评估
 * 并在变更流程中执行，系统不改任何参数。
 *
 * <p>数据来源均为已采集数据（天级参数快照 + 分钟级运行指标最新值），不连目标库。
 */
@Component
class ParamAdviceRuleEngine {

    // ---- 数值参数编码 ----
    private static final String P_BP_SIZE = "mysql.var.innodb_buffer_pool_size";
    private static final String P_MAX_CONN = "mysql.var.max_connections";
    private static final String P_TMP_TABLE = "mysql.var.tmp_table_size";
    private static final String P_MAX_HEAP = "mysql.var.max_heap_table_size";
    private static final String P_TABLE_CACHE = "mysql.var.table_open_cache";
    private static final String P_LONG_QUERY = "mysql.var.long_query_time";
    private static final String P_QCACHE_SIZE = "mysql.var.query_cache_size";

    // ---- 文本参数编码 ----
    private static final String P_FLUSH_LOG = "mysql.var_text.innodb_flush_log_at_trx_commit";
    private static final String P_SYNC_BINLOG = "mysql.var_text.sync_binlog";
    private static final String P_SLOW_LOG = "mysql.var_text.slow_query_log";
    private static final String P_GENERAL_LOG = "mysql.var_text.general_log";
    private static final String P_VERSION = "mysql.var_text.version";

    // ---- 运行指标编码 ----
    private static final String M_BP_HIT = "mysql.innodb.buffer_pool_hit_rate";
    private static final String M_CONN_USAGE = "mysql.conn.usage";
    private static final String M_CONN_TOTAL = "mysql.conn.total";
    private static final String M_TMP_TABLES = "mysql.delta.created_tmp_tables";
    private static final String M_TMP_DISK = "mysql.delta.created_tmp_disk_tables";
    private static final String M_OPENED_TABLES = "mysql.rate.Opened_tables";
    private static final String M_QPS = "mysql.qps";

    private final TsParamQueryDao paramQueryDao;
    private final TsMetricLatestDao metricLatestDao;

    ParamAdviceRuleEngine(TsParamQueryDao paramQueryDao, TsMetricLatestDao metricLatestDao) {
        this.paramQueryDao = paramQueryDao;
        this.metricLatestDao = metricLatestDao;
    }
    List<ParamAdviceVo> adviseMySql(Long instanceId) {
        Map<String, Double> vars = paramQueryDao.latestNumericParams(instanceId, List.of(
                P_BP_SIZE, P_MAX_CONN, P_TMP_TABLE, P_MAX_HEAP, P_TABLE_CACHE, P_LONG_QUERY, P_QCACHE_SIZE));
        Map<String, String> texts = paramQueryDao.latestTextParams(instanceId, List.of(
                P_FLUSH_LOG, P_SYNC_BINLOG, P_SLOW_LOG, P_GENERAL_LOG, P_VERSION));
        Map<String, Double> metrics = metricLatestDao.latestFrom1m(instanceId, List.of(
                M_BP_HIT, M_CONN_USAGE, M_CONN_TOTAL, M_TMP_TABLES, M_TMP_DISK, M_OPENED_TABLES, M_QPS));

        List<ParamAdviceVo> advices = new ArrayList<>();
        checkBufferPool(advices, vars, metrics);
        checkMaxConnections(advices, vars, metrics);
        checkTmpTable(advices, vars, metrics);
        checkTableOpenCache(advices, vars, metrics);
        checkQueryCache(advices, vars, texts.get(P_VERSION));
        checkDurability(advices, texts);
        checkSlowLog(advices, texts, vars);
        checkGeneralLog(advices, texts);
        return advices;
    }

    // ==================== PostgreSQL 参数体检 ====================

    // ---- PG 数值参数编码（PgSettingsItem 天级快照） ----
    private static final String PGP_SHARED_BUFFERS = "pg.setting.shared_buffers_bytes";
    private static final String PGP_EFF_CACHE = "pg.setting.effective_cache_size_bytes";
    private static final String PGP_WORK_MEM = "pg.setting.work_mem_bytes";
    private static final String PGP_MAINT_MEM = "pg.setting.maintenance_work_mem_bytes";
    private static final String PGP_MAX_WAL = "pg.setting.max_wal_size_bytes";
    private static final String PGP_MAX_CONN = "pg.setting.max_connections";
    private static final String PGP_IDLE_TRX_TIMEOUT = "pg.setting.idle_in_trx_timeout_ms";
    private static final String PGP_STMT_TIMEOUT = "pg.setting.statement_timeout_ms";

    // ---- PG 文本参数编码 ----
    private static final String PGP_SSL = "pg.setting_text.ssl";
    private static final String PGP_LOG_MIN_DURATION = "pg.setting_text.log_min_duration_statement";

    // ---- PG 运行指标编码 ----
    private static final String PGM_CACHE_HIT = "pg.cache.hit_rate";
    private static final String PGM_CONN_USAGE = "pg.conn.usage";
    private static final String PGM_CONN_TOTAL = "pg.conn.total";
    private static final String PGM_TEMP_FILES = "pg.delta.temp_files";
    private static final String PGM_CKPT_REQ = "pg.ckpt.req_delta";
    private static final String PGM_IDLE_TRX_MAX = "pg.trx.idle_in_trx_max_seconds";
    private static final String PGM_DEAD_PCT_MAX = "pg.bloat.dead_pct_max";
    private static final String PGM_HOST_MEM_AVAIL = "host.mem.available_bytes";
    private static final String PGM_HOST_MEM_USAGE = "host.mem.usage";

    /**
     * PG 参数体检 8 条：配置快照（天级）+ 运行指标（分钟/小时级）联合判断。
     * 与 MySQL 同一约束：只出建议不出手。
     */
    List<ParamAdviceVo> advisePg(Long instanceId) {
        Map<String, Double> vars = paramQueryDao.latestNumericParams(instanceId, List.of(
                PGP_SHARED_BUFFERS, PGP_EFF_CACHE, PGP_WORK_MEM, PGP_MAINT_MEM,
                PGP_MAX_WAL, PGP_MAX_CONN, PGP_IDLE_TRX_TIMEOUT, PGP_STMT_TIMEOUT));
        Map<String, String> texts = paramQueryDao.latestTextParams(instanceId, List.of(
                PGP_SSL, PGP_LOG_MIN_DURATION));
        Map<String, Double> m1 = metricLatestDao.latestFrom1m(instanceId, List.of(
                PGM_CACHE_HIT, PGM_CONN_USAGE, PGM_CONN_TOTAL, PGM_TEMP_FILES,
                PGM_CKPT_REQ, PGM_IDLE_TRX_MAX, PGM_HOST_MEM_AVAIL, PGM_HOST_MEM_USAGE));
        Map<String, Double> m1h = metricLatestDao.latestFrom1h(instanceId, List.of(PGM_DEAD_PCT_MAX));

        List<ParamAdviceVo> advices = new ArrayList<>();
        pgCheckSharedBuffers(advices, vars, m1);
        pgCheckEffectiveCacheSize(advices, vars, m1);
        pgCheckWorkMem(advices, vars, m1);
        pgCheckMaxWalSize(advices, vars, m1);
        pgCheckAutovacuumBloat(advices, vars, m1h);
        pgCheckIdleInTrxTimeout(advices, vars, m1);
        pgCheckStatementTimeout(advices, vars);
        pgCheckLogMinDuration(advices, texts);
        pgCheckSsl(advices, texts);
        return advices;
    }

    /** 从主机指标反推内存总量（可用字节 / (1-使用率)）；主机未关联或数据缺失返回 null。 */
    private static Double estimateHostMemTotal(Map<String, Double> m1) {
        Double avail = m1.get(PGM_HOST_MEM_AVAIL);
        Double usage = m1.get(PGM_HOST_MEM_USAGE);
        if (avail == null || usage == null || usage >= 100 || usage < 0) {
            return null;
        }
        return avail / (1 - usage / 100.0);
    }

    /** PG规则1：shared_buffers 与缓存命中率联判。 */
    private static void pgCheckSharedBuffers(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> m1) {
        Double sb = vars.get(PGP_SHARED_BUFFERS);
        if (sb == null) {
            return;
        }
        Double hit = m1.get(PGM_CACHE_HIT);
        Double memTotal = estimateHostMemTotal(m1);
        double pctOfMem = memTotal == null ? -1 : sb / memTotal * 100;
        boolean hitLow = hit != null && hit < 95.0;
        boolean tooSmall = pctOfMem >= 0 && pctOfMem < 10;
        boolean tooLarge = pctOfMem > 40;
        if (!hitLow && !tooSmall && !tooLarge) {
            return;
        }
        StringBuilder obs = new StringBuilder();
        if (hitLow) {
            obs.append("shared_buffers 命中率当前 ").append(fmt1(hit)).append("%（健康线 95%），磁盘读偏多");
        }
        if (tooSmall) {
            obs.append(obs.isEmpty() ? "" : "；").append("当前仅占主机内存约 ").append(fmt1(pctOfMem)).append('%');
        }
        if (tooLarge) {
            obs.append(obs.isEmpty() ? "" : "；").append("当前占主机内存约 ").append(fmt1(pctOfMem))
               .append("%，PG 还依赖操作系统页缓存，配置过大反而挤占系统缓存");
        }
        out.add(advice("shared_buffers", "共享缓冲区大小", fmtBytes(sb),
                obs.toString(),
                "经验起点为主机内存的 25% 左右（与 MySQL Buffer Pool 不同，不宜配到 50% 以上）；"
                        + "命中率低时先结合 Top SQL 确认是否大表全表扫描拉低命中率，优先补索引再考虑加内存。"
                        + "修改需重启实例，请在低峰窗口执行",
                hitLow && hit < 90 ? "warning" : "info"));
    }

    /** PG规则2：effective_cache_size 配置矛盾（小于 shared_buffers 或明显低于可用缓存）。 */
    private static void pgCheckEffectiveCacheSize(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> m1) {
        Double eff = vars.get(PGP_EFF_CACHE);
        Double sb = vars.get(PGP_SHARED_BUFFERS);
        if (eff == null || sb == null) {
            return;
        }
        Double memTotal = estimateHostMemTotal(m1);
        boolean belowSb = eff < sb;
        boolean farBelowMem = memTotal != null && eff < memTotal * 0.25;
        if (!belowSb && !farBelowMem) {
            return;
        }
        out.add(advice("effective_cache_size", "有效缓存大小估计", fmtBytes(eff),
                belowSb
                        ? "配置值小于 shared_buffers（" + fmtBytes(sb) + "），属于明显的配置矛盾——"
                          + "该参数是给优化器的\"可用缓存总量\"估计，偏小会使优化器低估索引扫描收益"
                        : "配置值约为主机内存的 " + fmt1(eff / memTotal * 100) + "%，明显低于常规估计",
                "建议设为主机内存的 50%~75%（shared_buffers + 操作系统页缓存的合计估计）；"
                        + "该参数不分配内存、在线可改（reload 生效），调整风险低",
                belowSb ? "warning" : "info"));
    }

    /** PG规则3：临时文件持续产生 → work_mem 偏小或存在大排序 SQL；同时提示总内存风险。 */
    private static void pgCheckWorkMem(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> m1) {
        Double tempFiles = m1.get(PGM_TEMP_FILES);
        if (tempFiles == null || tempFiles < 3) {
            return;
        }
        Double workMem = vars.get(PGP_WORK_MEM);
        Double maxConn = vars.get(PGP_MAX_CONN);
        String memRisk = (workMem != null && maxConn != null)
                ? "注意 work_mem 为每个排序/哈希节点独享，极限总消耗约 work_mem × 并发排序数"
                  + "（当前 max_connections=" + fmt0(maxConn) + "，全部打满理论上限 " + fmtBytes(workMem * maxConn) + "），盲目调大有 OOM 风险"
                : "注意 work_mem 为每个排序/哈希节点独享，盲目调大有 OOM 风险";
        out.add(advice("work_mem", "排序/哈希工作内存",
                workMem == null ? "-" : fmtBytes(workMem),
                "本周期新增落盘临时文件 " + fmt0(tempFiles) + " 个：排序/哈希/物化超过 work_mem 后落盘，拖慢查询并抢占磁盘 IO",
                "优先定位落盘大户 SQL（Top SQL 按临时块写入排序，或开启 log_temp_files）并优化；"
                        + "确需调整时可只对报表类会话 SET work_mem。" + memRisk,
                tempFiles >= 20 ? "warning" : "info"));
    }

    /** PG规则4：请求检查点出现 → max_wal_size 偏小。 */
    private static void pgCheckMaxWalSize(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> m1) {
        Double ckptReq = m1.get(PGM_CKPT_REQ);
        if (ckptReq == null || ckptReq < 1) {
            return;
        }
        Double maxWal = vars.get(PGP_MAX_WAL);
        out.add(advice("max_wal_size", "WAL 触发检查点上限",
                maxWal == null ? "-" : fmtBytes(maxWal),
                "最近采集周期出现「WAL 写满被迫触发」的请求检查点：写入量超出 max_wal_size 承载，"
                        + "频繁请求检查点会造成周期性 IO 抖动与全页写放大",
                "评估调大 max_wal_size（写入密集实例常设 4~16GB），目标是让检查点回归 checkpoint_timeout 定时触发；"
                        + "代价是崩溃恢复时间与 pg_wal 占用增加。在线可改（reload 生效）",
                "warning"));
    }

    /** PG规则5：表膨胀偏高 → autovacuum 参数联判。 */
    private static void pgCheckAutovacuumBloat(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> m1h) {
        Double deadPct = m1h.get(PGM_DEAD_PCT_MAX);
        if (deadPct == null || deadPct < 20) {
            return;
        }
        Double maintMem = vars.get(PGP_MAINT_MEM);
        out.add(advice("autovacuum_vacuum_scale_factor 等", "autovacuum 回收参数",
                maintMem == null ? "-" : ("maintenance_work_mem=" + fmtBytes(maintMem)),
                "存在死元组占比 " + fmt1(deadPct) + "% 的表（健康线 20%），autovacuum 回收速度跟不上写入",
                "先排查是否有长事务钉住 vacuum 水位（根因常在这里，调参无效）；确属 autovacuum 吞吐不足时，"
                        + "对大表按表设置 autovacuum_vacuum_scale_factor（如 0.01~0.02），"
                        + "并评估调大 autovacuum_max_workers 与 maintenance_work_mem",
                deadPct >= 40 ? "warning" : "info"));
    }

    /** PG规则6：idle_in_transaction_session_timeout 未设置且已出现事务中空闲。 */
    private static void pgCheckIdleInTrxTimeout(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> m1) {
        Double timeout = vars.get(PGP_IDLE_TRX_TIMEOUT);
        if (timeout == null || timeout > 0) {
            return;
        }
        Double idleMax = m1.get(PGM_IDLE_TRX_MAX);
        boolean observed = idleMax != null && idleMax >= 300;
        out.add(advice("idle_in_transaction_session_timeout", "事务中空闲超时", "0（未启用）",
                observed
                        ? "未设置兜底超时，且当前已存在空闲 " + fmt0(idleMax) + " 秒的事务中连接——"
                          + "该类连接持锁并钉住 VACUUM 水位，是 PG 膨胀与锁等待的常见根因"
                        : "未设置兜底超时：应用一旦泄漏\"开事务不提交\"的连接，将长期阻碍 VACUUM 并持有锁",
                "建议设置 5~10 分钟（如 '600s'）作为兜底，超时连接自动断开（事务回滚）；"
                        + "根治仍需应用侧修复事务边界。在线可改（reload 生效）",
                observed ? "warning" : "info"));
    }

    /** PG规则7：statement_timeout 未设置提示。 */
    private static void pgCheckStatementTimeout(List<ParamAdviceVo> out, Map<String, Double> vars) {
        Double timeout = vars.get(PGP_STMT_TIMEOUT);
        if (timeout == null || timeout > 0) {
            return;
        }
        out.add(advice("statement_timeout", "语句执行超时", "0（未启用）",
                "未设置全局语句超时：失控查询（笛卡尔积/无界扫描）可长时间占用连接与 IO",
                "建议按业务容忍度设置全局兜底（如 '60s'），并对报表/批处理会话单独放宽；"
                        + "注意过小的全局值会误杀正常长任务，建议先观察 Top SQL 的最大耗时分布再定值",
                "info"));
    }

    /** PG规则8：log_min_duration_statement 未开启 → 慢SQL样本阈值回退默认。 */
    private static void pgCheckLogMinDuration(List<ParamAdviceVo> out, Map<String, String> texts) {
        String v = texts.get(PGP_LOG_MIN_DURATION);
        if (!StringUtils.hasText(v)) {
            return;
        }
        String trimmed = v.trim();
        if (!"-1".equals(trimmed)) {
            return;
        }
        out.add(advice("log_min_duration_statement", "慢语句记录阈值", "-1（未启用）",
                "未设置慢语句阈值：本平台的慢SQL样本采集将回退默认 1 秒阈值，且数据库日志中无慢语句留痕",
                "建议设置 1~2 秒（如 '1s'，在线可改）：平台采样与数据库日志将按该阈值识别慢语句",
                "info"));
    }

    /** PG规则9：SSL 未开启 → 安全提示。 */
    private static void pgCheckSsl(List<ParamAdviceVo> out, Map<String, String> texts) {
        String ssl = texts.get(PGP_SSL);
        if (!StringUtils.hasText(ssl) || !"off".equalsIgnoreCase(ssl.trim())) {
            return;
        }
        out.add(advice("ssl", "连接加密", "off",
                "实例未启用 SSL：客户端与数据库之间的流量（含认证信息与业务数据）明文传输",
                "跨主机访问或有合规要求时建议启用 SSL（配置证书后 reload 生效）；"
                        + "纯内网同机部署可结合安全要求评估",
                "info"));
    }

    // ==================== MySQL 参数体检 ====================

    /** 规则1：Buffer Pool 命中率偏低 → 建议评估增大 innodb_buffer_pool_size。 */
    private static void checkBufferPool(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> metrics) {
        Double hit = metrics.get(M_BP_HIT);
        if (hit == null || hit >= 95.0) {
            return;
        }
        Double size = vars.get(P_BP_SIZE);
        out.add(advice("innodb_buffer_pool_size", "InnoDB 缓冲池大小",
                size == null ? "-" : fmtBytes(size),
                "Buffer Pool 命中率当前 " + fmt1(hit) + "%（健康线 95%），存在明显物理读",
                "在主机内存允许的前提下评估增大缓冲池（一般建议为专用数据库服务器内存的 50%~70%）；"
                        + "同时结合慢SQL分析确认是否存在大范围全表扫描把热数据挤出缓冲池",
                hit < 90 ? "warning" : "info"));
    }

    /** 规则2：连接使用率过高 → max_connections 接近打满。 */
    private static void checkMaxConnections(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> metrics) {
        Double usage = metrics.get(M_CONN_USAGE);
        if (usage == null || usage < 80.0) {
            return;
        }
        Double max = vars.get(P_MAX_CONN);
        Double total = metrics.get(M_CONN_TOTAL);
        out.add(advice("max_connections", "最大连接数",
                max == null ? "-" : fmt0(max),
                "连接使用率当前 " + fmt1(usage) + "%"
                        + (total == null ? "" : "（当前连接 " + fmt0(total) + "）") + "，接近上限后新连接将被拒绝",
                "优先排查应用连接池是否泄漏 / 配置过大；确属业务增长再评估上调 max_connections"
                        + "（注意每连接约消耗数 MB 内存，上调前核对主机内存余量）",
                usage >= 90 ? "warning" : "info"));
    }

    /** 规则3：磁盘临时表占比高 → tmp_table_size / max_heap_table_size 偏小或 SQL 需优化。 */
    private static void checkTmpTable(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> metrics) {
        Double tmp = metrics.get(M_TMP_TABLES);
        Double disk = metrics.get(M_TMP_DISK);
        if (tmp == null || disk == null || tmp < 10 || disk / Math.max(tmp, 1) < 0.25) {
            return;
        }
        double pct = disk / tmp * 100;
        Double tmpSize = vars.get(P_TMP_TABLE);
        Double heapSize = vars.get(P_MAX_HEAP);
        out.add(advice("tmp_table_size / max_heap_table_size", "内存临时表上限",
                (tmpSize == null ? "-" : fmtBytes(tmpSize)) + " / " + (heapSize == null ? "-" : fmtBytes(heapSize)),
                "本周期磁盘临时表占比 " + fmt1(pct) + "%（内存临时表 " + fmt0(tmp) + "，转磁盘 " + fmt0(disk) + "），"
                        + "大量排序/分组结果落盘会显著拖慢查询",
                "两个参数取小者生效，需同时评估上调；含 TEXT/BLOB 列的结果集必然落盘，"
                        + "优先结合慢SQL分析优化相关查询（减少 SELECT *、加合适索引）",
                pct >= 50 ? "warning" : "info"));
    }

    /** 规则4：表打开速率高 → table_open_cache 偏小。 */
    private static void checkTableOpenCache(List<ParamAdviceVo> out, Map<String, Double> vars, Map<String, Double> metrics) {
        Double openedRate = metrics.get(M_OPENED_TABLES);
        Double qps = metrics.get(M_QPS);
        // 低业务量下的表打开多为冷启动，不判定
        if (openedRate == null || openedRate < 10 || (qps != null && qps < 10)) {
            return;
        }
        Double cache = vars.get(P_TABLE_CACHE);
        out.add(advice("table_open_cache", "表缓存大小",
                cache == null ? "-" : fmt0(cache),
                "表打开速率当前 " + fmt1(openedRate) + " 次/秒，持续偏高说明表缓存频繁淘汰重开",
                "评估上调 table_open_cache（需同步确认 open_files_limit 足够）；"
                        + "表数量特别多的实例建议同时上调 table_open_cache_instances",
                "info"));
    }

    /** 规则5：5.6/5.7 启用查询缓存 → 建议关闭（高并发锁争用）。 */
    private static void checkQueryCache(List<ParamAdviceVo> out, Map<String, Double> vars, String dbVersion) {
        Double qcache = vars.get(P_QCACHE_SIZE);
        if (qcache == null || qcache <= 0) {
            return;
        }
        // 8.0 已移除查询缓存，此参数只会在 5.6/5.7 上采到
        out.add(advice("query_cache_size", "查询缓存大小", fmtBytes(qcache),
                "查询缓存已启用（MySQL " + nullSafe(dbVersion) + "）。写入会使相关缓存全部失效，"
                        + "高并发下查询缓存互斥锁常成为瓶颈，官方已在 8.0 移除该特性",
                "除非是极端读多写少且命中率经实测很高的场景，建议设置 query_cache_type=0、query_cache_size=0 关闭",
                "info"));
    }

    /** 规则6：非双一配置 → 数据安全提示（不强制改，可能是有意的性能取舍）。 */
    private static void checkDurability(List<ParamAdviceVo> out, Map<String, String> texts) {
        String flush = texts.get(P_FLUSH_LOG);
        String sync = texts.get(P_SYNC_BINLOG);
        boolean flushRisk = StringUtils.hasText(flush) && !"1".equals(flush.trim());
        boolean syncRisk = StringUtils.hasText(sync) && !"1".equals(sync.trim());
        if (!flushRisk && !syncRisk) {
            return;
        }
        out.add(advice("innodb_flush_log_at_trx_commit / sync_binlog", "事务持久化（双一）设置",
                nullSafe(flush) + " / " + nullSafe(sync),
                "当前非\"双一\"配置：实例或主机意外宕机时可能丢失最近若干秒的已提交事务"
                        + (syncRisk ? "，且 binlog 可能与数据不一致（影响主从与按时间点恢复）" : ""),
                "核心业务建议恢复双一（两参数均为 1）；若为性能压测/可容忍丢数据场景的有意取舍，请确认相关方知晓风险",
                "warning"));
    }

    /** 规则7：慢查询日志未开启 / 阈值偏大。 */
    private static void checkSlowLog(List<ParamAdviceVo> out, Map<String, String> texts, Map<String, Double> vars) {
        String slowLog = texts.get(P_SLOW_LOG);
        Double longQuery = vars.get(P_LONG_QUERY);
        if (StringUtils.hasText(slowLog) && "OFF".equalsIgnoreCase(slowLog.trim())) {
            out.add(advice("slow_query_log", "慢查询日志", slowLog,
                    "慢查询日志未开启，无法留存慢 SQL 现场（本平台 5.6 慢SQL样本采集也依赖它）",
                    "建议开启：SET GLOBAL slow_query_log=ON（开销很小）；并将 long_query_time 设为 1~2 秒",
                    "warning"));
        } else if (longQuery != null && longQuery > 5) {
            out.add(advice("long_query_time", "慢查询阈值（秒）", fmt1(longQuery),
                    "阈值偏大：超过 " + fmt1(longQuery) + " 秒才记为慢查询，大量 1~" + fmt0(longQuery) + " 秒的问题 SQL 会漏采",
                    "建议下调至 1~2 秒（在线可改，立即生效）",
                    "info"));
        }
    }

    /** 规则8：general_log 开启 → 性能损耗提示。 */
    private static void checkGeneralLog(List<ParamAdviceVo> out, Map<String, String> texts) {
        String generalLog = texts.get(P_GENERAL_LOG);
        if (!StringUtils.hasText(generalLog) || !"ON".equalsIgnoreCase(generalLog.trim())) {
            return;
        }
        out.add(advice("general_log", "通用查询日志", generalLog,
                "通用查询日志处于开启状态：记录每一条到达的语句，磁盘与性能开销都很大，通常仅用于临时排障",
                "排障结束后建议关闭：SET GLOBAL general_log=OFF",
                "warning"));
    }

    // ---- 工具 ----

    private static ParamAdviceVo advice(String param, String display, String current,
                                        String observation, String suggestion, String level) {
        ParamAdviceVo vo = new ParamAdviceVo();
        vo.setParamName(param);
        vo.setDisplayName(display);
        vo.setCurrentValue(current);
        vo.setObservation(observation);
        vo.setAdvice(suggestion);
        vo.setLevel(level);
        return vo;
    }

    private static String fmtBytes(double b) {
        if (b >= 1073741824) {
            return trimZero(b / 1073741824) + " GB";
        }
        if (b >= 1048576) {
            return trimZero(b / 1048576) + " MB";
        }
        if (b >= 1024) {
            return trimZero(b / 1024) + " KB";
        }
        return fmt0(b) + " B";
    }

    private static String trimZero(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static String fmt0(double v) {
        return String.valueOf(Math.round(v));
    }

    private static String fmt1(double v) {
        return trimZero(Math.round(v * 10) / 10.0);
    }

    private static String nullSafe(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
