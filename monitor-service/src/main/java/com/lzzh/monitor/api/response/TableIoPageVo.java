package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 表 I/O 热点分页条目（近 1 小时差值）。 */
@Schema(description = "表 I/O 热点分页条目")
public class TableIoPageVo {

    @Schema(description = "库名")
    private String schemaName;

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "本周期 I/O 等待耗时（毫秒）")
    private double waitMs;

    @Schema(description = "本周期读操作次数")
    private long readCount;

    @Schema(description = "本周期写操作次数")
    private long writeCount;

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public double getWaitMs() { return waitMs; }
    public void setWaitMs(double waitMs) { this.waitMs = waitMs; }

    public long getReadCount() { return readCount; }
    public void setReadCount(long readCount) { this.readCount = readCount; }

    public long getWriteCount() { return writeCount; }
    public void setWriteCount(long writeCount) { this.writeCount = writeCount; }
}
