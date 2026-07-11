-- =============================================================
-- 用户个性化偏好（账号级主题持久化，方案 §8.5 分层主题：系统默认 + 用户覆盖）
-- =============================================================

CREATE TABLE sys_user_preference (
    user_id     BIGINT PRIMARY KEY,
    theme       JSONB,
    update_time TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_user_preference IS '用户个性化偏好（主题等），user_id 关联 sys_user';
COMMENT ON COLUMN sys_user_preference.theme IS '主题配置 JSON：mode/signalColor/density/layout/radius/fontSize/palette/tagsView 等';
