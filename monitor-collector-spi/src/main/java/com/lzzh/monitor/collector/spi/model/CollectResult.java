package com.lzzh.monitor.collector.spi.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 采集结果：标准化指标点集合（数值/文本/对象级/TopSQL）+ 状态。 */
@Data
public class CollectResult {

    private Long instanceId;
    /** 数值时序指标点（gauge / counter 加工后）。 */
    private List<MetricPoint> points = new ArrayList<>();
    /** 文本/状态指标点（覆盖变更存储）。 */
    private List<TextMetricPoint> textPoints = new ArrayList<>();
    /** 对象级数值指标点（容量明细等）。 */
    private List<ObjectMetricPoint> objectPoints = new ArrayList<>();
    /** Top SQL / digest 明细点。 */
    private List<TopSqlPoint> topSqlPoints = new ArrayList<>();
    /** PostgreSQL Query Analytics 周期增量。 */
    private List<PgQueryStatPoint> pgQueryStatPoints = new ArrayList<>();
    /** PostgreSQL 第三期标准化运维事件。 */
    private List<PgOperationalEventPoint> pgOperationalEventPoints = new ArrayList<>();
    /** 长连接明细点（processlist TIME >= 阈值的连接快照）。 */
    private List<LongConnPoint> longConnPoints = new ArrayList<>();
    /** 慢 SQL 真实执行样本点（events_statements_history 耗时超阈值语句）。 */
    private List<SlowSqlSamplePoint> slowSqlSamplePoints = new ArrayList<>();
    private boolean success = true;
    /** 整体失败时的错误信息（连接失败等）。 */
    private String error;
    /** 采集项级别的部分失败错误（item 内部异常，连接成功但部分指标采集失败）。 */
    private List<String> itemErrors = new ArrayList<>();

    public static CollectResult ok(Long instanceId, List<MetricPoint> points) {
        CollectResult r = new CollectResult();
        r.setInstanceId(instanceId);
        r.setPoints(points);
        r.setSuccess(true);
        return r;
    }

    public static CollectResult fail(Long instanceId, String error) {
        CollectResult r = new CollectResult();
        r.setInstanceId(instanceId);
        r.setSuccess(false);
        r.setError(error);
        return r;
    }

    /** 是否存在 item 级部分失败。 */
    public boolean hasItemErrors() {
        return itemErrors != null && !itemErrors.isEmpty();
    }

    public void addItemError(String itemCode, String message) {
        if (itemErrors == null) {
            itemErrors = new ArrayList<>();
        }
        itemErrors.add(itemCode + ": " + message);
    }
}
