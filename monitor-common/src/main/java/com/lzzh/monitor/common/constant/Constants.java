package com.lzzh.monitor.common.constant;

/** 全局常量：权限码前缀、缓存 Key、分片默认值等。 */
public final class Constants {

    private Constants() {
    }

    /** 超级管理员通配权限码。 */
    public static final String PERM_ALL = "*:*";

    /** 超级管理员角色编码：拥有全部权限与全量数据范围，不受数据范围校验限制。 */
    public static final String SUPER_ADMIN_ROLE = "super_admin";

    /** 字典类型范围：系统级（仅超管可维护）。 */
    public static final String DICT_SCOPE_SYSTEM = "system";

    /** 字典类型范围：自定义（按按钮权限维护）。 */
    public static final String DICT_SCOPE_CUSTOM = "custom";

    /** Redis 缓存 Key 前缀。 */
    public static final String CACHE_PREFIX = "monitor:";
    public static final String CACHE_INSTANCE = CACHE_PREFIX + "instance:";
    public static final String CACHE_RULE = CACHE_PREFIX + "rule:";

    /** 采集分片默认值。 */
    public static final int DEFAULT_SHARD_TOTAL = 1;
    public static final int DEFAULT_SHARD_INDEX = 0;

    /** 当前登录用户在请求属性中的 Key。 */
    public static final String REQ_ATTR_LOGIN_USER = "loginUser";

    /**
     * 系统内置连接失败告警的规则编码：不对应 alert_rule 行（事件 rule_id 为 NULL），
     * 事件 dedup_key 格式为 {@code system.connection_failure:{instanceId}}，
     * 采集侧建单（ConnectionFailureAlertService）与服务侧事件识别共用此编码。
     */
    public static final String SYSTEM_RULE_CONNECTION_FAILURE = "system.connection_failure";
}
