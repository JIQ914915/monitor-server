-- PostgreSQL 运维健康在暂无历史数据时使用独立字典状态，避免误显示为“正常”。
INSERT INTO sys_dict_item (dict_type,item_value,item_label,tag_type,sort,status)
SELECT 'pg_operation_severity','no_data','数据不足','info',4,'enabled'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item
     WHERE dict_type='pg_operation_severity' AND item_value='no_data'
);
