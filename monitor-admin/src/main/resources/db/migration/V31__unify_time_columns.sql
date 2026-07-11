-- =============================================================
-- V31: 全库业务表时间列统一（§21.2 约定）
--   create_time/update_time (TIMESTAMP)  ->  created_at/updated_at (TIMESTAMPTZ)
--   并绑定全局 set_updated_at() 触发器（V23 已建函数），实现「修改即更新」。
--   彻底取代 V17 的 stopgap：配合实体字段由 LocalDateTime 改为 OffsetDateTime，
--   PG 驱动可正常读写 TIMESTAMPTZ，不再出现 V17 的类型转换报错。
--   幂等：列名/触发器均带存在性判断，可重复执行。
--   存量 TIMESTAMP（无时区）按 Asia/Shanghai 解释为带时区时间。
-- 说明：sys_oper_log.oper_time（审计时刻）、sys_user.last_login_time 语义非
--       created_at/updated_at，不在本次统一范围内。
-- =============================================================
DO $$
DECLARE
    t     text;
    -- 含 create_time + update_time 两列的业务表
    tbls  text[] := ARRAY[
        'sys_user', 'db_instance', 'instance_group', 'sys_menu',
        'sys_role', 'knowledge_article', 'sys_dict_type', 'sys_dict_item'
    ];
BEGIN
    FOREACH t IN ARRAY tbls LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = t AND column_name = 'create_time') THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN create_time TO created_at', t);
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = t AND column_name = 'update_time') THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN update_time TO updated_at', t);
        END IF;

        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE ''Asia/Shanghai''', t);
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE ''Asia/Shanghai''', t);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN created_at SET DEFAULT now()', t);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN updated_at SET DEFAULT now()', t);

        EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_updated_at ON %I', t, t);
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
    END LOOP;

    -- sys_user_preference：仅 update_time -> updated_at
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'sys_user_preference' AND column_name = 'update_time') THEN
        ALTER TABLE sys_user_preference RENAME COLUMN update_time TO updated_at;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'sys_user_preference' AND column_name = 'updated_at') THEN
        ALTER TABLE sys_user_preference
            ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Shanghai';
        ALTER TABLE sys_user_preference ALTER COLUMN updated_at SET DEFAULT now();
        DROP TRIGGER IF EXISTS trg_sys_user_preference_updated_at ON sys_user_preference;
        CREATE TRIGGER trg_sys_user_preference_updated_at BEFORE UPDATE ON sys_user_preference
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
END $$;
