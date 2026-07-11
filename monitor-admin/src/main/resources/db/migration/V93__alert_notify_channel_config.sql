-- =============================================================
-- V93：告警通知通道全局配置
--   Webhook / 钉钉 / 企业微信 / 飞书机器人的地址与签名密钥改为全局统一维护
--   （系统管理 → 通知通道），告警规则里只保留通道勾选，
--   避免每个实例 × 每条规则重复维护 URL/密钥。
--   邮件/短信的服务端参数仍在部署配置（application.yml）中维护，不入库。
-- =============================================================

CREATE TABLE alert_notify_channel_config (
    id          BIGSERIAL PRIMARY KEY,
    channel     VARCHAR(20)  NOT NULL,                     -- 通道：webhook/dingtalk/wecom/feishu
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,       -- 全局开关：关闭后勾选了该通道的规则不再发送
    config      JSONB        NOT NULL DEFAULT '{}'::jsonb, -- {"urls":[...],"secret":"enc:..."}（secret 加密存储）
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_alert_notify_channel_config UNIQUE (channel),
    CONSTRAINT ck_alert_notify_channel_config CHECK (channel IN ('webhook', 'dingtalk', 'wecom', 'feishu'))
);
COMMENT ON TABLE alert_notify_channel_config IS '告警通知通道全局配置（规则仅勾选通道，地址/密钥统一在此维护）';
COMMENT ON COLUMN alert_notify_channel_config.channel IS '通道：webhook/dingtalk/wecom/feishu';
COMMENT ON COLUMN alert_notify_channel_config.enabled IS '全局开关：关闭后勾选了该通道的规则不再发送';
COMMENT ON COLUMN alert_notify_channel_config.config IS '通道参数：urls=地址列表；secret=签名密钥（钉钉/飞书，PasswordCipher 加密）';

CREATE TRIGGER trg_alert_notify_channel_config_updated_at BEFORE UPDATE ON alert_notify_channel_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 预置四个通道行（默认停用，待管理员配置）
INSERT INTO alert_notify_channel_config (channel, enabled) VALUES
    ('webhook', FALSE),
    ('dingtalk', FALSE),
    ('wecom', FALSE),
    ('feishu', FALSE)
ON CONFLICT (channel) DO NOTHING;

-- ── 菜单：系统管理 → 通知通道 ────────────────────────────────────────────────
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('通知通道', 'system_notify_channel', 'menu', '系统级', 'Bell',
        'notify-channel', 'system/notify-channel',
        'notify_channel:list', 18, 'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'system'),
        '告警通知通道全局配置：Webhook/钉钉/企业微信/飞书机器人地址与签名密钥',
        '[{"name":"编辑","code":"notify_channel:update","status":"enabled"}]'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ── 清理规则中内嵌的通道参数（改由全局配置承载，评估/发送侧不再读取） ─────────
UPDATE alert_rule_instance_config
   SET notification_config = notification_config
       - 'webhookUrl' - 'webhookUrls'
       - 'dingtalkUrl' - 'dingtalkUrls' - 'dingtalkSecret'
       - 'wecomUrl' - 'wecomUrls'
       - 'feishuUrl' - 'feishuUrls' - 'feishuSecret'
 WHERE notification_config ?| ARRAY['webhookUrl','webhookUrls','dingtalkUrl','dingtalkUrls','dingtalkSecret',
                                    'wecomUrl','wecomUrls','feishuUrl','feishuUrls','feishuSecret'];

UPDATE alert_rule
   SET notification_config = notification_config
       - 'webhookUrl' - 'webhookUrls'
       - 'dingtalkUrl' - 'dingtalkUrls' - 'dingtalkSecret'
       - 'wecomUrl' - 'wecomUrls'
       - 'feishuUrl' - 'feishuUrls' - 'feishuSecret'
 WHERE notification_config ?| ARRAY['webhookUrl','webhookUrls','dingtalkUrl','dingtalkUrls','dingtalkSecret',
                                    'wecomUrl','wecomUrls','feishuUrl','feishuUrls','feishuSecret'];
