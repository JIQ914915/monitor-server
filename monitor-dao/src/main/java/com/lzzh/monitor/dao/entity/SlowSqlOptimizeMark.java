package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** SQL 指纹优化状态人工标记（无记录视为未优化）。 */
@Data
@TableName("slow_sql_optimize_mark")
public class SlowSqlOptimizeMark {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    /** 库名（与 metric_top_sql.schema_name 一致，可空）。 */
    private String schemaName;

    /** SQL 指纹。 */
    private String digest;

    /** 优化状态：字典 slow_sql_optimize_status（unoptimized/optimized）。 */
    private String status;

    private String updatedBy;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
