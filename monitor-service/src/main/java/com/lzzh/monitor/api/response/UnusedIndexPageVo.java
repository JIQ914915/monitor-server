package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 疑似未使用索引分页响应。
 * <p>{@code list/total} 由外层 {@code PageResult} 承载；本 VO 仅补充 uptime 提示字段，
 * 因此分页接口返回专用包装，避免前端再解析整包 JSON。
 */
@Schema(description = "疑似未使用索引分页响应")
public class UnusedIndexPageVo {

    @Schema(description = "实例启动以来运行天数（P_S 计数累计窗口）")
    private long uptimeDays;

    @Schema(description = "当前页索引列表")
    private List<Item> list;

    @Schema(description = "总条数")
    private long total;

    public long getUptimeDays() { return uptimeDays; }
    public void setUptimeDays(long uptimeDays) { this.uptimeDays = uptimeDays; }

    public List<Item> getList() { return list; }
    public void setList(List<Item> list) { this.list = list; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    @Schema(description = "疑似未使用索引条目")
    public static class Item {
        @Schema(description = "库名")
        private String schemaName;
        @Schema(description = "表名")
        private String tableName;
        @Schema(description = "索引名")
        private String indexName;

        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public String getIndexName() { return indexName; }
        public void setIndexName(String indexName) { this.indexName = indexName; }
    }
}
