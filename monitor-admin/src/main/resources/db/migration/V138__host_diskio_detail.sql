-- =============================================================
-- V138：主机磁盘 IO 按盘明细
--   host.diskio.util_max 只反映最繁忙的一块盘，定位不了具体设备。
--   新增文本指标 host.diskio.detail：每块盘（Linux 块设备 / Windows 盘符）
--   的 IO 繁忙度与读写速率 JSON，前端主机资源页展示明细表。
-- =============================================================

INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('host.diskio.detail', '磁盘 IO 按盘明细', 'host', 'host', 'explain', 'text', NULL,
 'host.node_exporter', 'delta', '1m',
 '各磁盘 IO 明细 JSON：[{"device":"sda|C:","utilPercent":...,"readBytes":...,"writeBytes":...}]，'
 '按繁忙度倒序；Linux 为块设备、Windows 为盘符口径，速率为两次采样差值换算的每秒值')
ON CONFLICT (metric_code) DO NOTHING;
