-- =============================================================
-- 用户/角色管理完善：
--   sys_user 新增 邮箱/电话/最后登录时间
--   sys_role 新增 类型(预设/自定义)/状态/描述/创建时间
-- =============================================================

-- ---- 用户 ----
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS email           VARCHAR(128);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS phone           VARCHAR(32);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS last_login_time TIMESTAMP;
COMMENT ON COLUMN sys_user.email IS '邮箱';
COMMENT ON COLUMN sys_user.phone IS '联系电话';
COMMENT ON COLUMN sys_user.last_login_time IS '最后登录时间';

-- ---- 角色 ----
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS type        VARCHAR(16)  NOT NULL DEFAULT '自定义';
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS status      VARCHAR(16)  NOT NULL DEFAULT 'enabled';
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS description VARCHAR(255);
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS create_time TIMESTAMP    NOT NULL DEFAULT now();
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS update_time TIMESTAMP    NOT NULL DEFAULT now();
COMMENT ON COLUMN sys_role.type IS '角色类型：预设/自定义（预设不可删除）';
COMMENT ON COLUMN sys_role.status IS '状态：enabled/disabled';
COMMENT ON COLUMN sys_role.description IS '角色描述';

-- 内置角色标记为「预设」，并补充描述
UPDATE sys_role SET type = '预设', description = COALESCE(description, '系统内置角色')
 WHERE code IN ('super_admin', 'dba', 'ops', 'auditor');
