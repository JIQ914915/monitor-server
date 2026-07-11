-- V57：alert_rule 表补充 category（规则分类）和 description（描述）字段
ALTER TABLE alert_rule
    ADD COLUMN IF NOT EXISTS category    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);

COMMENT ON COLUMN alert_rule.category    IS '规则分类：performance/connection/resource/availability';
COMMENT ON COLUMN alert_rule.description IS '规则描述';
