-- =============================================================
-- V137：主机操作系统类型字典
--   host.os_type 由隐藏默认值（linux）升级为登记时显式选择：
--   Linux 主机部署 node_exporter、Windows 主机部署 windows_exporter，
--   前端按类型裁剪主机资源展示（Windows 无 iowait/swap/inode/load 语义）。
-- =============================================================

INSERT INTO sys_dict_type (dict_type, dict_name, type, remark) VALUES
  ('host_os_type', '主机操作系统类型', 'system',
   '决定 exporter 部署形态与主机资源指标口径：linux=node_exporter / windows=windows_exporter')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('host_os_type', 'linux',   'Linux',   'primary', 1),
  ('host_os_type', 'windows', 'Windows', 'success', 2)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item d
   WHERE d.dict_type = v.dict_type AND d.item_value = v.item_value);

COMMENT ON COLUMN host.os_type IS '操作系统类型（字典 host_os_type）：linux=node_exporter / windows=windows_exporter';
