-- =============================================================
-- V145：LLM 智能分析（§11.7.4 / 模块6 可选增强）
--
--   1. llm_config      —— 全局唯一配置（OpenAI 兼容接口 + 数据不出域开关 + 脱敏开关）
--   2. llm_analysis    —— 告警事件智能分析结果（按事件缓存，可重新生成）
--   3. 菜单：系统设置 → 智能分析设置（system/llm）
--   4. 角色权限：llm_config:view/update（配置）、llm:analyze（下钻页生成分析）
--
--   安全边界：LLM 仅生成总结与建议，不执行任何处置动作；输出必须标注
--   "AI 生成，仅供参考"；api_key 经 PasswordCipher 加密落库，查询只回显掩码。
-- =============================================================

-- ---- 1. 全局配置（单行，id 固定 1）----
CREATE TABLE IF NOT EXISTS llm_config (
    id              BIGINT PRIMARY KEY,
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- OpenAI 兼容接口地址（到 /v1 为止，如 http://ollama.internal:11434/v1）
    base_url        VARCHAR(255),
    -- API Key（enc: 前缀 AES-GCM 密文；本地部署可为空）
    api_key         VARCHAR(512),
    model           VARCHAR(128),
    timeout_seconds INT          NOT NULL DEFAULT 60,
    -- 数据不出域开关：FALSE=仅允许内网/本机地址（默认），TRUE=允许调用公网大模型服务
    allow_external  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 发送前对上下文脱敏（IP 打码、SQL 字面量参数化）
    desensitize     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE llm_config IS 'LLM 智能分析全局配置（单行）：OpenAI 兼容接口、数据不出域开关、脱敏开关';

INSERT INTO llm_config (id, enabled) VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;

-- ---- 2. 分析结果（每事件缓存最新一次，重新生成覆盖）----
CREATE TABLE IF NOT EXISTS llm_analysis (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT       NOT NULL UNIQUE,
    success         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 事件总结（一段话）
    summary         TEXT,
    -- 可能原因（字符串数组）
    causes          JSONB,
    -- 处理建议（字符串数组；仅建议，须人工确认后执行）
    suggestions     JSONB,
    error_message   VARCHAR(500),
    model           VARCHAR(128),
    prompt_chars    INT,
    response_chars  INT,
    duration_ms     BIGINT,
    operator_id     BIGINT,
    operator_name   VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE llm_analysis IS '告警事件 LLM 智能分析结果（AI 生成，仅供参考；含调用审计元数据）';

-- ---- 3. 菜单：系统设置 → 智能分析设置 ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('智能分析设置', 'llm_config', 'menu', '系统级', 'MagicStick',
        'llm', 'system/llm',
        'llm_config:view', 22, 'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'system'),
        'LLM 智能分析配置：OpenAI 兼容接口、数据不出域开关、上下文脱敏；仅生成总结与建议，不做自动处置',
        '[
          {"name": "保存配置", "code": "llm_config:update", "status": "enabled"}
        ]'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ---- 4. 预设角色权限 ----
-- super_admin：*:* 通配，无需单独添加
-- dba / ops：可在事件下钻页生成智能分析；dba 可查看配置
UPDATE sys_role
   SET permissions = permissions || '["llm:analyze", "llm_config:view"]'::jsonb
 WHERE code = 'dba'
   AND NOT (permissions @> '["llm:analyze"]'::jsonb);

UPDATE sys_role
   SET permissions = permissions || '["llm:analyze"]'::jsonb
 WHERE code = 'ops'
   AND NOT (permissions @> '["llm:analyze"]'::jsonb);
