-- =============================================================
-- V134：主机指标监控（二）——host.* 指标定义种子
--   采集来源：主机侧 node_exporter，monitor-collector 分钟级 HTTP 拉取解析。
--   存储：按「主机 → 关联实例扇出」写入 metric_data_1m（文本指标写 metric_text_data_1m），
--   与实例指标共用时序表、告警评估与趋势查询链路。
--   counter 类（process_type=delta）首个采样周期只建基线不产出，第二个周期起输出速率。
-- =============================================================

INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
-- ---- 可用性 ----
('host.availability', '主机可达性', 'host', 'host', 'guard', 'numeric', 'count',
 'host.node_exporter', 'raw', '1m',
 '1 = 主机 exporter HTTP 可达，0 = 拉取失败；连续 3 次为 0 时主机 status 自动标为 abnormal'),
('host.uptime', '主机运行时长', 'host', 'host', 'explain', 'numeric', 'count',
 'host.node_exporter', 'raw', '1m',
 '主机自启动以来的运行秒数（node_time_seconds - node_boot_time_seconds），骤降说明主机发生过重启'),
-- ---- CPU ----
('host.cpu.usage', 'CPU 使用率', 'host', 'host', 'guard', 'numeric', 'percent',
 'host.node_exporter', 'delta', '1m',
 '两次采样间 CPU 非空闲时间占比（node_cpu_seconds_total 全核汇总，非 idle 占比），持续高说明主机算力吃紧'),
('host.cpu.iowait', 'CPU IOWait 占比', 'host', 'host', 'guard', 'numeric', 'percent',
 'host.node_exporter', 'delta', '1m',
 '两次采样间 CPU 处于 IO 等待的时间占比，持续偏高通常意味着磁盘成为瓶颈'),
('host.load.load1', '1 分钟负载', 'host', 'host', 'analysis', 'numeric', 'count',
 'host.node_exporter', 'raw', '1m',
 '系统 1 分钟平均负载（node_load1），需结合 CPU 核数解读'),
('host.load.load5', '5 分钟负载', 'host', 'host', 'analysis', 'numeric', 'count',
 'host.node_exporter', 'raw', '1m',
 '系统 5 分钟平均负载（node_load5）'),
('host.load.per_core', '单核负载', 'host', 'host', 'guard', 'numeric', 'count',
 'host.node_exporter', 'ratio', '1m',
 '1 分钟平均负载 / CPU 核数，大于 1 表示任务开始排队，大于 2 说明主机明显过载'),
-- ---- 内存 ----
('host.mem.usage', '内存使用率', 'host', 'host', 'guard', 'numeric', 'percent',
 'host.node_exporter', 'ratio', '1m',
 '1 - MemAvailable/MemTotal，基于内核可用内存估算，比 free 更准确反映内存压力'),
('host.mem.available_bytes', '可用内存', 'host', 'host', 'analysis', 'numeric', 'bytes',
 'host.node_exporter', 'raw', '1m',
 '内核估算的可分配内存（node_memory_MemAvailable_bytes），过低时数据库有被 OOM 的风险'),
('host.swap.usage', 'Swap 使用率', 'host', 'host', 'guard', 'numeric', 'percent',
 'host.node_exporter', 'ratio', '1m',
 '1 - SwapFree/SwapTotal；未配置 Swap 时记 0。数据库主机出现 Swap 使用通常伴随明显性能抖动'),
-- ---- 磁盘空间 ----
('host.disk.usage_max', '磁盘使用率（最高挂载点）', 'host', 'host', 'guard', 'numeric', 'percent',
 'host.node_exporter', 'ratio', '1m',
 '各本地文件系统挂载点使用率的最大值（排除 tmpfs/overlay 等虚拟文件系统），挂载点明细见 host.disk.mount_detail'),
('host.disk.inode_usage_max', 'Inode 使用率（最高挂载点）', 'host', 'host', 'guard', 'numeric', 'percent',
 'host.node_exporter', 'ratio', '1m',
 '各挂载点 inode 使用率的最大值，inode 耗尽时即使磁盘有空间也无法创建新文件'),
('host.disk.readonly_count', '只读文件系统数', 'host', 'host', 'guard', 'numeric', 'count',
 'host.node_exporter', 'raw', '1m',
 '处于只读挂载状态的文件系统数量（node_filesystem_readonly），大于 0 通常意味着磁盘故障或文件系统错误'),
-- ---- 磁盘 IO ----
('host.diskio.util_max', '磁盘 IO 繁忙度（最高设备）', 'host', 'host', 'guard', 'numeric', 'percent',
 'host.node_exporter', 'delta', '1m',
 '各块设备 IO 时间占比（node_disk_io_time_seconds_total 速率）的最大值，接近 100% 说明磁盘已饱和'),
('host.diskio.read_bytes', '磁盘读吞吐', 'host', 'host', 'analysis', 'numeric', 'bytes',
 'host.node_exporter', 'delta', '1m',
 '全部块设备每秒读取字节数（node_disk_read_bytes_total 速率汇总）'),
('host.diskio.write_bytes', '磁盘写吞吐', 'host', 'host', 'analysis', 'numeric', 'bytes',
 'host.node_exporter', 'delta', '1m',
 '全部块设备每秒写入字节数（node_disk_written_bytes_total 速率汇总）'),
-- ---- 网络 ----
('host.net.recv_bytes', '网络入流量', 'host', 'host', 'analysis', 'numeric', 'bytes',
 'host.node_exporter', 'delta', '1m',
 '全部物理网卡每秒接收字节数（排除 lo 回环）'),
('host.net.send_bytes', '网络出流量', 'host', 'host', 'analysis', 'numeric', 'bytes',
 'host.node_exporter', 'delta', '1m',
 '全部物理网卡每秒发送字节数（排除 lo 回环）'),
-- ---- 文本明细 ----
('host.disk.mount_detail', '挂载点使用明细', 'host', 'host', 'explain', 'text', NULL,
 'host.node_exporter', 'state', '1m',
 '各挂载点使用率明细 JSON：[{"mount":"/","total":...,"avail":...,"usagePercent":...}]，变更时覆盖存储')
ON CONFLICT (metric_code) DO NOTHING;
