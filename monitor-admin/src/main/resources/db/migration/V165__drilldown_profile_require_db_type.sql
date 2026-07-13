-- 下钻画像必须显式声明数据库类型，禁止空值或数据库默认值静默落入 MySQL。
ALTER TABLE alert_drilldown_profile
    ALTER COLUMN db_type DROP DEFAULT;

ALTER TABLE alert_drilldown_profile
    DROP CONSTRAINT IF EXISTS chk_alert_drilldown_profile_db_type_not_blank;

ALTER TABLE alert_drilldown_profile
    ADD CONSTRAINT chk_alert_drilldown_profile_db_type_not_blank
        CHECK (btrim(db_type) <> '');