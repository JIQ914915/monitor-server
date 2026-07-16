package com.lzzh.monitor.service.metric;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.ParamPageRequest;
import com.lzzh.monitor.api.response.*;
import com.lzzh.monitor.common.enums.MetricRole;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.MysqlParamMeta;
import com.lzzh.monitor.dao.ts.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MetricQueryServiceImpl implements MetricQueryService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_DAYS = 30;

    private static final int DEFAULT_OBJECT_LIMIT = 20;

    /** 性能监控图表默认时间窗口：最近 1 小时（毫秒）。 */
    private static final long DEFAULT_TREND_WINDOW_MS = 60L * 60 * 1000;

    /** 性能分析批量趋势默认时间窗口：最近 24 小时（毫秒）。 */
    private static final long DEFAULT_BATCH_TREND_WINDOW_MS = 24L * 60 * 60 * 1000;

    /** 数值型参数白名单（与 VariablesItem.WANTED 保持同步）。 */
    private static final List<String> NUMERIC_PARAM_CODES = Arrays.asList(
            "mysql.var.max_connections",
            "mysql.var.innodb_buffer_pool_size",
            "mysql.var.innodb_log_file_size",
            "mysql.var.innodb_log_files_in_group",
            "mysql.var.max_allowed_packet",
            "mysql.var.max_binlog_size",
            "mysql.var.binlog_expire_logs_seconds",
            "mysql.var.expire_logs_days",
            "mysql.var.performance_schema_digests_size",
            "mysql.var.performance_schema_max_digest_length",
            "mysql.var.performance_schema_max_sql_text_length",
            "mysql.var.table_open_cache",
            "mysql.var.thread_cache_size",
            "mysql.var.open_files_limit",
            "mysql.var.wait_timeout",
            "mysql.var.long_query_time",
            "mysql.var.tmp_table_size",
            "mysql.var.query_cache_size"
    );

    /** 文本型参数白名单（与 VariablesItem.WANTED_TEXT 保持同步）。 */
    private static final List<String> TEXT_PARAM_CODES = Arrays.asList(
            "mysql.var_text.sql_mode",
            "mysql.var_text.performance_schema",
            "mysql.var_text.query_cache_type",
            "mysql.var_text.version",
            "mysql.var_text.time_zone",
            "mysql.var_text.character_set_server",
            "mysql.var_text.innodb_flush_log_at_trx_commit",
            "mysql.var_text.sync_binlog",
            "mysql.var_text.log_bin",
            "mysql.var_text.binlog_format",
            "mysql.var_text.gtid_mode",
            "mysql.var_text.enforce_gtid_consistency",
            "mysql.var_text.slow_query_log",
            "mysql.var_text.log_error",
            "mysql.var_text.general_log"
    );

    /** PG 数值型参数白名单（与 PgSettingsItem.NUMERIC_SETTINGS 保持同步）。 */
    private static final List<String> PG_NUMERIC_PARAM_CODES = Arrays.asList(
            "pg.setting.max_connections",
            "pg.setting.shared_buffers_bytes",
            "pg.setting.effective_cache_size_bytes",
            "pg.setting.work_mem_bytes",
            "pg.setting.maintenance_work_mem_bytes",
            "pg.setting.max_wal_size_bytes",
            "pg.setting.checkpoint_timeout_seconds",
            "pg.setting.autovacuum_max_workers",
            "pg.setting.max_worker_processes",
            "pg.setting.idle_in_trx_timeout_ms",
            "pg.setting.statement_timeout_ms"
    );

    /** PG 文本型参数白名单（与 PgSettingsItem.TEXT_SETTINGS 保持同步）。 */
    private static final List<String> PG_TEXT_PARAM_CODES = Arrays.asList(
            "pg.setting_text.server_version",
            "pg.setting_text.wal_level",
            "pg.setting_text.archive_mode",
            "pg.setting_text.hot_standby",
            "pg.setting_text.autovacuum",
            "pg.setting_text.ssl",
            "pg.setting_text.shared_preload_libraries",
            "pg.setting_text.log_min_duration_statement"
    );

    @Resource
    private TsCapacityGrowthDao capacityGrowthDao;
    @Resource
    private TsMetricTrendDao metricTrendDao;
    @Resource
    private TsMetricLatestDao metricLatestDao;
    @Resource
    private TsMetricObjectDao metricObjectDao;
    @Resource
    private TsLongConnDao longConnDao;
    @Resource
    private TsParamQueryDao paramQueryDao;
    @Resource
    private TsTableGrowthDao tableGrowthDao;
    @Resource
    private TsTempTableStatsDao tempTableStatsDao;
    @Resource
    private TsTextReader textReader;
    @Resource
    private ParamMetaService paramMetaService;
    @Resource
    private com.lzzh.monitor.dao.mapper.DbInstanceMapper dbInstanceMapper;
    @Resource
    private DbTypeResolver dbTypeResolver;
    @Resource
    private DatabaseMetricCatalogRegistry metricCatalogRegistry;


    private DatabaseMetricCatalog metricCatalog(Long instanceId) {
        return metricCatalogRegistry.get(dbTypeResolver.resolve(instanceId));
    }
    @Override
    public CapacityGrowthVo capacityGrowthTrend(Long instanceId, int days) {
        int effectiveDays = Math.max(MIN_DAYS, Math.min(MAX_DAYS, days <= 0 ? DEFAULT_DAYS : days));
        List<TsCapacityGrowthDao.CapacityGrowthPoint> points = capacityGrowthDao.queryGrowthTrend(instanceId, effectiveDays);
        CapacityGrowthVo result = new CapacityGrowthVo();
        result.setInstanceId(instanceId);
        result.setTrend(points.stream().map(point -> {
            CapacityGrowthVo.DailyPoint item = new CapacityGrowthVo.DailyPoint();
            item.setDay(point.day().toString());
            item.setCurrentBytes(point.currentBytes());
            item.setPrevWeekBytes(point.prevWeekBytes());
            item.setGrowthBytes(point.growthBytes());
            item.setGrowthRatePct(point.growthRatePct());
            return item;
        }).toList());
        return result;
    }

    @Override
    public CapacityForecastVo capacityForecast(Long instanceId) {
        List<TsCapacityGrowthDao.CapacityGrowthPoint> points = capacityGrowthDao.queryGrowthTrend(instanceId, DEFAULT_DAYS);
        CapacityForecastVo result = new CapacityForecastVo();
        result.setInstanceId(instanceId);
        if (points.isEmpty()) { result.setPredictionStatus("insufficient"); result.setNote("暂无日级容量快照，无法进行容量预测"); return result; }
        TsCapacityGrowthDao.CapacityGrowthPoint first = points.getFirst();
        TsCapacityGrowthDao.CapacityGrowthPoint last = points.getLast();
        result.setCurrentBytes(last.currentBytes());
        int sampleDays = (int) Math.max(0, last.day().toEpochDay() - first.day().toEpochDay());
        result.setSampleDays(sampleDays);
        if (sampleDays < 7) { result.setPredictionStatus("insufficient"); result.setNote("容量样本不足 7 天，暂无法形成可靠预测"); return result; }
        double dailyGrowth = (last.currentBytes() - first.currentBytes()) / (double) sampleDays;
        result.setDailyGrowthBytes(dailyGrowth);
        result.setDailyGrowth30dBytes(dailyGrowth);
        List<TsCapacityGrowthDao.CapacityGrowthPoint> recent7 = points.size() <= 8 ? points : points.subList(points.size() - 8, points.size());
        TsCapacityGrowthDao.CapacityGrowthPoint first7 = recent7.getFirst();
        TsCapacityGrowthDao.CapacityGrowthPoint last7 = recent7.getLast();
        int days7 = (int) Math.max(1, last7.day().toEpochDay() - first7.day().toEpochDay());
        result.setDailyGrowth7dBytes((last7.currentBytes() - first7.currentBytes()) / (double) days7);
        DbInstance instance = dbInstanceMapper.selectById(instanceId);
        if (instance == null || instance.getHostId() == null) { result.setPredictionStatus("insufficient"); result.setNote("实例未关联主机，无法读取数据盘剩余空间"); return result; }
        String json = textReader.latestFrom1m(instance.getHostId(), List.of("host.disk.mount_detail")).get("host.disk.mount_detail");
        DiskCapacity disk = largestDisk(json);
        if (disk == null) { result.setPredictionStatus("insufficient"); result.setNote("关联主机暂无数据盘挂载点明细，无法估算剩余天数"); return result; }
        result.setDiskMount(disk.mount()); result.setDiskTotalBytes(disk.totalBytes()); result.setDiskAvailBytes(disk.availBytes()); result.setDiskUsagePercent(disk.usagePercent());
        if (dailyGrowth <= 0) { result.setPredictionStatus("stable"); result.setNote("最近容量未增长，暂无耗尽风险预测"); return result; }
        result.setEstimatedDaysRemaining((int) Math.min(3650, Math.ceil(disk.availBytes() / dailyGrowth)));
        result.setEstimatedExhaustionDate(java.time.LocalDate.now().plusDays(result.getEstimatedDaysRemaining()).toString());
        result.setPredictionStatus(result.getEstimatedDaysRemaining() <= 90 ? "risk" : "stable");
        result.setNote("按最近 " + sampleDays + " 天日均增长线性估算；异常波动仅作风险提示");
        return result;
    }

    private DiskCapacity largestDisk(String json) {
        if (json == null || json.isBlank() || !cn.hutool.json.JSONUtil.isTypeJSONArray(json)) return null;
        DiskCapacity selected = null;
        for (Object value : cn.hutool.json.JSONUtil.parseArray(json)) {
            if (!(value instanceof cn.hutool.json.JSONObject disk)) continue;
            Long total = disk.getLong("totalBytes"); Long available = disk.getLong("availBytes");
            if (total == null || total <= 0 || available == null) continue;
            DiskCapacity candidate = new DiskCapacity(disk.getStr("mount"), total, available, disk.getDouble("usagePercent"));
            if (selected == null || candidate.totalBytes() > selected.totalBytes()) selected = candidate;
        }
        return selected;
    }

    private record DiskCapacity(String mount, long totalBytes, long availBytes, Double usagePercent) {}
    @Override
    public MetricTrendVo metricTrend(Long instanceId, String metricCode, long from, long to, String frequency) {
        // from/to 为 0 表示调用方未传入，使用默认最近 1 小时
        long effectiveTo   = to   <= 0 ? System.currentTimeMillis() : to;
        long effectiveFrom = from <= 0 ? effectiveTo - DEFAULT_TREND_WINDOW_MS : from;
        String freq = (frequency == null || frequency.isBlank()) ? "1m" : frequency;
        List<TsMetricTrendDao.TrendPoint> points = metricTrendDao.queryTrendByFrequency(
                instanceId, metricCode, effectiveFrom, effectiveTo, freq);
        MetricTrendVo vo = new MetricTrendVo();
        vo.setInstanceId(instanceId);
        vo.setMetricCode(metricCode);
        vo.setPoints(points.stream()
                .map(p -> new MetricTrendVo.Point(p.ts(), p.value()))
                .toList());
        return vo;
    }

    @Override
    public PerfTrendBatchVo perfTrendBatch(Long instanceId, List<String> metricCodes,
                                           long from, long to, String frequency) {
        long effectiveTo   = to   <= 0 ? System.currentTimeMillis() : to;
        long effectiveFrom = from <= 0 ? effectiveTo - DEFAULT_BATCH_TREND_WINDOW_MS : from;
        String freq = "1m".equalsIgnoreCase(frequency) ? "1m" : "1h";

        List<PerfTrendBatchVo.Series> series = new ArrayList<>(metricCodes.size());
        for (String code : metricCodes) {
            List<TsMetricTrendDao.TrendPoint> points = metricTrendDao.queryTrendByFrequency(
                    instanceId, code, effectiveFrom, effectiveTo, freq);
            series.add(new PerfTrendBatchVo.Series(code, points.stream()
                    .map(p -> new MetricTrendVo.Point(p.ts(), p.value()))
                    .toList()));
        }
        PerfTrendBatchVo vo = new PerfTrendBatchVo();
        vo.setInstanceId(instanceId);
        vo.setFrequency(freq);
        vo.setSeries(series);
        return vo;
    }

    @Override
    public MetricLatestVo latestMetrics(Long instanceId, List<String> metricCodes) {
        // 优先查 metric_data_1m（分钟级指标）
        Map<String, Double> raw = new HashMap<>(metricLatestDao.latestFrom1m(instanceId, metricCodes));

        // 1m 中未命中的 code（日级指标，如 mysql.var.*）降级查 metric_data_1d
        List<String> missing = metricCodes.stream()
                .filter(code -> raw.get(code) == null)
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            raw.putAll(metricLatestDao.latestFrom1d(instanceId, missing));
        }

        // 对请求的所有 code 补 null（确保前端能感知哪些无数据）
        Map<String, Double> values = new HashMap<>();
        for (String code : metricCodes) {
            values.put(code, raw.get(code));
        }
        MetricLatestVo vo = new MetricLatestVo();
        vo.setInstanceId(instanceId);
        vo.setValues(values);
        return vo;
    }

    @Override
    public MetricObjectVo metricObjects(Long instanceId, String metricCode, int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_OBJECT_LIMIT : limit;
        List<TsMetricObjectDao.ObjectPoint> points =
                metricObjectDao.queryTopN(instanceId, metricCode, effectiveLimit);

        MetricObjectVo vo = new MetricObjectVo();
        vo.setInstanceId(instanceId);
        vo.setMetricCode(metricCode);
        vo.setItems(points.stream().map(p -> {
            MetricObjectVo.Item item = new MetricObjectVo.Item();
            item.setObjectName(p.objectName());
            item.setObjectType(p.objectType());
            item.setValue(p.value());
            item.setCollectTimeMs(p.collectTimeMs());
            return item;
        }).toList());
        return vo;
    }

    private static final String TABLEIO_WAIT_MS = "tableio.wait_ms";
    private static final String TABLEIO_READ = "tableio.read_count";
    private static final String TABLEIO_WRITE = "tableio.write_count";
    private static final String UNUSED_INDEX_LIST = "mysql.index.unused_list";
    private static final String PG_UNUSED_INDEX_LIST = "pg.index.unused_list";

    @Override
    public PageResult<TableIoPageVo> tableIoPage(Long instanceId, long pageNum, long pageSize) {
        long pn = Math.max(1, pageNum);
        long ps = Math.max(1, Math.min(100, pageSize <= 0 ? 10 : pageSize));
        long total = metricObjectDao.countLatest(instanceId, TABLEIO_WAIT_MS);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        int offset = (int) ((pn - 1) * ps);
        List<TsMetricObjectDao.ObjectPoint> waitPage =
                metricObjectDao.queryPage(instanceId, TABLEIO_WAIT_MS, offset, (int) ps);
        if (waitPage.isEmpty()) {
            return PageResult.of(List.of(), total);
        }
        List<String> names = waitPage.stream().map(TsMetricObjectDao.ObjectPoint::objectName).toList();
        Map<String, Double> readMap = metricObjectDao.queryLatestValuesByNames(instanceId, TABLEIO_READ, names);
        Map<String, Double> writeMap = metricObjectDao.queryLatestValuesByNames(instanceId, TABLEIO_WRITE, names);

        List<TableIoPageVo> list = waitPage.stream().map(p -> {
            TableIoPageVo row = new TableIoPageVo();
            String[] parts = splitObjectName(p.objectName());
            row.setSchemaName(parts[0]);
            row.setTableName(parts[1]);
            row.setWaitMs(p.value());
            row.setReadCount(Math.round(readMap.getOrDefault(p.objectName(), 0D)));
            row.setWriteCount(Math.round(writeMap.getOrDefault(p.objectName(), 0D)));
            return row;
        }).toList();
        return PageResult.of(list, total);
    }

    @Override
    public UnusedIndexPageVo unusedIndexPage(Long instanceId, long pageNum, long pageSize) {
        long pn = Math.max(1, pageNum);
        long ps = Math.max(1, Math.min(100, pageSize <= 0 ? 10 : pageSize));

        UnusedIndexPageVo vo = new UnusedIndexPageVo();
        vo.setUptimeDays(0);
        vo.setList(List.of());
        vo.setTotal(0);

        String listCode = metricCatalog(instanceId).codeOf(MetricRole.UNUSED_INDEX_LIST);
        Map<String, String> raw = textReader.latestFrom1d(instanceId, List.of(listCode));
        String json = raw.get(listCode);
        if (json == null || json.isBlank() || !cn.hutool.json.JSONUtil.isTypeJSONObject(json)) {
            return vo;
        }
        cn.hutool.json.JSONObject root = cn.hutool.json.JSONUtil.parseObj(json);
        vo.setUptimeDays(root.getLong("uptimeDays", 0L));
        cn.hutool.json.JSONArray arr = root.getJSONArray("indexes");
        if (arr == null || arr.isEmpty()) {
            return vo;
        }
        int total = arr.size();
        vo.setTotal(total);
        int from = (int) ((pn - 1) * ps);
        if (from >= total) {
            return vo;
        }
        int to = Math.min(from + (int) ps, total);
        List<UnusedIndexPageVo.Item> page = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            cn.hutool.json.JSONObject node = arr.getJSONObject(i);
            if (node == null) {
                continue;
            }
            UnusedIndexPageVo.Item item = new UnusedIndexPageVo.Item();
            // 采集端字段为 schema/table/index
            item.setSchemaName(node.getStr("schema", node.getStr("schemaName", "")));
            item.setTableName(node.getStr("table", node.getStr("tableName", "")));
            item.setIndexName(node.getStr("index", node.getStr("indexName", "")));
            page.add(item);
        }
        vo.setList(page);
        return vo;
    }

    /** 将 schema.table 拆成 [schema, table]；无点号时表名为空串。 */
    private static String[] splitObjectName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return new String[]{"", ""};
        }
        int dot = objectName.indexOf('.');
        if (dot < 0) {
            return new String[]{objectName, ""};
        }
        return new String[]{objectName.substring(0, dot), objectName.substring(dot + 1)};
    }

    @Override
    public LongConnVo longConnections(Long instanceId) {
        List<TsLongConnDao.LongConnRow> rows = longConnDao.queryLatest(instanceId);
        LongConnVo vo = new LongConnVo();
        vo.setInstanceId(instanceId);
        vo.setConnections(rows.stream().map(r -> {
            LongConnVo.ConnRow row = new LongConnVo.ConnRow();
            row.setConnId(r.connId());
            row.setConnUser(r.connUser());
            row.setConnHost(r.connHost());
            row.setConnDb(r.connDb());
            row.setCommand(r.command());
            row.setTimeSeconds(r.timeSeconds());
            row.setState(r.state());
            row.setInfo(r.info());
            row.setCollectTimeMs(r.collectTimeMs());
            return row;
        }).toList());
        return vo;
    }

    @Override
    public ParamCurrentVo paramsCurrent(Long instanceId) {
        DatabaseMetricCatalog catalog = metricCatalog(instanceId);
        List<String> numericCodes = catalog.numericParameterCodes();
        List<String> textCodes = catalog.textParameterCodes();
        String numericPrefix = catalog.numericParameterPrefix();
        String textPrefix = catalog.textParameterPrefix();

        Map<String, Double> numericValues = paramQueryDao.latestNumericParams(instanceId, numericCodes);
        Map<String, String> textValues = paramQueryDao.latestTextParams(instanceId, textCodes);

        List<ParamCurrentVo.ParamItem> items = new ArrayList<>();

        for (String code : numericCodes) {
            ParamCurrentVo.ParamItem item = new ParamCurrentVo.ParamItem();
            item.setMetricCode(code);
            item.setParamName(code.substring(numericPrefix.length()));
            item.setValueType("numeric");
            Double v = numericValues.get(code);
            if (v != null) {
                item.setHasValue(true);
                // 整数参数去小数点（如 max_connections = 151.0 → "151"）
                item.setValue(v == Math.floor(v) && !Double.isInfinite(v)
                        ? String.valueOf(v.longValue()) : String.valueOf(v));
            } else {
                item.setHasValue(false);
            }
            items.add(item);
        }

        for (String code : textCodes) {
            ParamCurrentVo.ParamItem item = new ParamCurrentVo.ParamItem();
            item.setMetricCode(code);
            item.setParamName(code.substring(textPrefix.length()));
            item.setValueType("text");
            if (textValues.containsKey(code)) {
                item.setHasValue(true);
                item.setValue(textValues.get(code));
            } else {
                item.setHasValue(false);
            }
            items.add(item);
        }

        // 按参数名排序，便于前端展示
        items.sort((a, b) -> a.getParamName().compareTo(b.getParamName()));

        ParamCurrentVo vo = new ParamCurrentVo();
        vo.setInstanceId(instanceId);
        vo.setParams(items);
        return vo;
    }

    @Override
    public TableGrowthVo tableGrowth(Long instanceId, String metricCode, int limit) {
        int effectiveLimit = limit <= 0 ? 50 : limit;
        List<TsTableGrowthDao.TableGrowthRow> rows =
                tableGrowthDao.queryTopN(instanceId, metricCode, effectiveLimit);

        TableGrowthVo vo = new TableGrowthVo();
        vo.setInstanceId(instanceId);
        vo.setMetricCode(metricCode);
        vo.setTables(rows.stream().map(r -> {
            TableGrowthVo.TableRow row = new TableGrowthVo.TableRow();
            row.setObjectName(r.objectName());
            row.setObjectType(r.objectType());
            row.setCurrentBytes(r.currentBytes());
            row.setPrevWeekBytes(r.prevWeekBytes());
            row.setGrowthBytes(r.growthBytes());
            row.setGrowthRatePct(r.growthRatePct());
            return row;
        }).toList());
        return vo;
    }

    @Override
    public TodayStatsVo todayStats(Long instanceId) {
        TsTempTableStatsDao.TodayStats stats = tempTableStatsDao.queryTodayStats(instanceId);
        TodayStatsVo vo = new TodayStatsVo();
        vo.setInstanceId(instanceId);
        vo.setTmpTablesToday(stats.tmpTablesToday());
        vo.setTmpDiskTablesToday(stats.tmpDiskTablesToday());
        vo.setSlowQueriesToday(stats.slowQueriesToday());
        double diskRatio = stats.tmpTablesToday() > 0
                ? Math.round((stats.tmpDiskTablesToday() * 10000.0 / stats.tmpTablesToday())) / 100.0
                : 0.0;
        vo.setDiskRatioPct(diskRatio);
        return vo;
    }

    @Override
    public MetricTextVo latestTextMetrics(Long instanceId, List<String> metricCodes, String frequency) {
        Map<String, String> raw;
        if ("1m".equalsIgnoreCase(frequency)) {
            raw = textReader.latestFrom1m(instanceId, metricCodes);
        } else if ("1h".equalsIgnoreCase(frequency)) {
            raw = textReader.latestFrom1h(instanceId, metricCodes);
        } else {
            raw = textReader.latestFrom1d(instanceId, metricCodes);
        }

        // 对请求的所有 code 补 null
        Map<String, String> values = new HashMap<>();
        for (String code : metricCodes) {
            values.put(code, raw.get(code));
        }
        MetricTextVo vo = new MetricTextVo();
        vo.setInstanceId(instanceId);
        vo.setValues(values);
        return vo;
    }

    private static final String DISK_IO_DETAIL_CODE = "host.diskio.detail";

    @Override
    public HostDiskIoTrendVo hostDiskIoTrend(Long instanceId, long from, long to) {
        long effectiveTo   = to   <= 0 ? System.currentTimeMillis() : to;
        long effectiveFrom = from <= 0 ? effectiveTo - DEFAULT_BATCH_TREND_WINDOW_MS : from;

        // 盘名 → 三组时间序列（TreeMap 保证盘名字典序稳定）
        Map<String, HostDiskIoTrendVo.DeviceSeries> byDevice = new java.util.TreeMap<>();
        for (TsTextReader.TextHistoryRow row : textReader.rangeFrom1m(
                instanceId, DISK_IO_DETAIL_CODE, effectiveFrom, effectiveTo)) {
            String json = row.valueText();
            if (json == null || !cn.hutool.json.JSONUtil.isTypeJSONArray(json)) {
                continue;
            }
            for (Object item : cn.hutool.json.JSONUtil.parseArray(json)) {
                if (!(item instanceof cn.hutool.json.JSONObject node)) {
                    continue;
                }
                String device = node.getStr("device");
                if (device == null) {
                    continue;
                }
                HostDiskIoTrendVo.DeviceSeries series = byDevice.computeIfAbsent(device, k -> {
                    HostDiskIoTrendVo.DeviceSeries s = new HostDiskIoTrendVo.DeviceSeries();
                    s.setDevice(k);
                    s.setUtil(new ArrayList<>());
                    s.setRead(new ArrayList<>());
                    s.setWrite(new ArrayList<>());
                    return s;
                });
                appendPoint(series.getUtil(), row.collectTimeMs(), node.getDouble("utilPercent"));
                appendPoint(series.getRead(), row.collectTimeMs(), node.getDouble("readBytes"));
                appendPoint(series.getWrite(), row.collectTimeMs(), node.getDouble("writeBytes"));
            }
        }

        HostDiskIoTrendVo vo = new HostDiskIoTrendVo();
        vo.setInstanceId(instanceId);
        vo.setDevices(List.copyOf(byDevice.values()));
        return vo;
    }

    private static void appendPoint(List<MetricTrendVo.Point> points, long ts, Double value) {
        if (value != null) {
            points.add(new MetricTrendVo.Point(ts, value));
        }
    }

    @Override
    public MetricTextVo.HistoryVo textMetricHistory(Long instanceId, String metricCode) {
        List<TsTextReader.TextHistoryRow> rows = textReader.historyFrom1d(instanceId, metricCode);
        MetricTextVo.HistoryVo vo = new MetricTextVo.HistoryVo();
        vo.setInstanceId(instanceId);
        vo.setMetricCode(metricCode);
        vo.setHistory(rows.stream().map(r -> {
            MetricTextVo.HistoryItem item = new MetricTextVo.HistoryItem();
            item.setMetricCode(r.metricCode());
            item.setValueText(r.valueText());
            item.setCollectTimeMs(r.collectTimeMs());
            return item;
        }).toList());
        return vo;
    }

    @Override
    public PageResult<ParamPageItemVo> paramsPage(ParamPageRequest req) {
        // 1. 在 mysql_param_meta 上做真实 DB 分页（MyBatis-Plus LIMIT/OFFSET）
        Page<MysqlParamMeta> metaPage = paramMetaService.page(req);
        List<com.lzzh.monitor.dao.entity.MysqlParamMeta> metaList = metaPage.getRecords();
        if (metaList.isEmpty()) {
            return PageResult.of(List.of(), metaPage.getTotal());
        }

        // 2. 为本页参数名构建指标编码列表（分别尝试 mysql.var.* 和 mysql.var_text.*）
        List<String> numericCodes = metaList.stream()
                .map(m -> "mysql.var." + m.getParamName())
                .filter(NUMERIC_PARAM_CODES::contains)
                .collect(Collectors.toList());
        List<String> textCodes = metaList.stream()
                .map(m -> "mysql.var_text." + m.getParamName())
                .filter(TEXT_PARAM_CODES::contains)
                .collect(Collectors.toList());

        // 3. 批量查询 TS 当前值（仅查本页涉及的 codes，避免全量扫描）
        Map<String, Double> numericValues = numericCodes.isEmpty() ? Map.of()
                : paramQueryDao.latestNumericParams(req.getInstanceId(), numericCodes);
        Map<String, String> textValues = textCodes.isEmpty() ? Map.of()
                : paramQueryDao.latestTextParams(req.getInstanceId(), textCodes);

        // 4. 合并元数据 + 当前值
        List<ParamPageItemVo> result = metaList.stream().map(meta -> {
            ParamPageItemVo v = new ParamPageItemVo();
            v.setParamName(meta.getParamName());
            v.setDisplayName(meta.getDisplayName());
            v.setCategory(meta.getCategory());
            v.setIsDynamic(meta.getIsDynamic());
            v.setUnit(meta.getUnit());
            v.setDescription(meta.getDescription());

            String numCode  = "mysql.var."      + meta.getParamName();
            String textCode = "mysql.var_text." + meta.getParamName();

            if (numericValues.containsKey(numCode)) {
                v.setMetricCode(numCode);
                v.setValueType("numeric");
                v.setHasValue(true);
                Double d = numericValues.get(numCode);
                v.setValue(d == Math.floor(d) && !Double.isInfinite(d)
                        ? String.valueOf(d.longValue()) : String.valueOf(d));
            } else if (textValues.containsKey(textCode)) {
                v.setMetricCode(textCode);
                v.setValueType("text");
                v.setHasValue(true);
                v.setValue(textValues.get(textCode));
            } else {
                v.setMetricCode(numCode);
                v.setValueType("numeric");
                v.setHasValue(false);
                v.setValue(null);
            }
            return v;
        }).collect(Collectors.toList());

        return PageResult.of(result, metaPage.getTotal());
    }
}
