-- 数据库类型码值统一使用 database_type.code 的大写形式。
-- 展示名称只使用 database_type.label，不再参与持久化关联或服务端分派。
-- 部分历史部署通过 baseline 跳过了早期可选表迁移，因此仅更新当前实际存在的表和列。
DO $$
DECLARE
    target record;
    target_relation regclass;
BEGIN
    FOR target IN
        SELECT *
          FROM (VALUES
                ('database_type', 'code'),
                ('database_version', 'db_type'),
                ('metric_definition', 'db_type'),
                ('collector_config', 'db_type'),
                ('capability_matrix', 'db_type'),
                ('alert_drilldown_profile', 'db_type')) AS targets(table_name, column_name)
    LOOP
        target_relation := to_regclass(target.table_name);
        IF target_relation IS NOT NULL
           AND EXISTS (
                SELECT 1
                  FROM pg_attribute
                 WHERE attrelid = target_relation
                   AND attname = target.column_name
                   AND NOT attisdropped
           ) THEN
            EXECUTE format(
                    'UPDATE %s SET %I = upper(btrim(%I)) WHERE %I <> upper(btrim(%I))',
                    target_relation,
                    target.column_name,
                    target.column_name,
                    target.column_name,
                    target.column_name);
        END IF;
    END LOOP;
END $$;
