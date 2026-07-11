-- =============================================================
-- V150：表 I/O 热点 / 疑似未使用索引分页支撑
--   采集保留上限上调（代码侧 TableIoStatItem：热点 200 / 未使用索引 500）
--   同步更新 metric_definition 说明，避免文档仍写 Top20 / 最多 50 条
-- =============================================================

UPDATE metric_definition
   SET description = 'performance_schema.table_io_waits_summary_by_table 差值：最近一小时该表的读操作（fetch）次数；'
                 || '对象指标（object_type=table），每轮按等待耗时保留 Top 200 的表，供管理端分页。5.7/8.0 采集'
 WHERE metric_code = 'tableio.read_count';

UPDATE metric_definition
   SET description = '最近一小时该表的写操作（insert/update/delete）次数，对象指标；与 wait_ms 同批保留 Top 200'
 WHERE metric_code = 'tableio.write_count';

UPDATE metric_definition
   SET description = '最近一小时该表的表级 I/O 等待累计耗时（毫秒）：等待事件大类之下的对象级下钻，'
                 || '回答"时间花在哪张表上"；排序即热点表排名，每轮保留 Top 200 供分页查询'
 WHERE metric_code = 'tableio.wait_ms';

UPDATE metric_definition
   SET description = 'table_io_waits_summary_by_index_usage 中 COUNT_STAR=0 的二级索引（排除 PRIMARY 与系统库，最多 500 条）：'
                 || '{"uptimeDays":N,"indexes":[{schema,table,index}]}；'
                 || '计数自实例启动累计，uptimeDays 较小时结论不可靠，删除索引前须人工确认；管理端按列表分页'
 WHERE metric_code = 'mysql.index.unused_list';
