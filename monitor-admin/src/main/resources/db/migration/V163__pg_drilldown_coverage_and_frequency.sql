-- PostgreSQL 下钻完整性：实例可用性、主机通用画像、临时文件场景精确匹配及指标频率。

INSERT INTO alert_drilldown_profile
    (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions,
     builtin, enabled, sort, remark)
VALUES (
    'pg_instance_availability', 'PG 实例可用性类', 'postgresql',
    '[{"matchType":"exact","pattern":"pg.availability"},{"matchType":"exact","pattern":"system.connection_failure"}]'::jsonb,
    '[{"code":"pg.availability","label":"PG 实例可达性","unit":"","color":"#E5484D"},
      {"code":"pg.uptime","label":"PG 运行时长","unit":"s","color":"#0C7C97"},
      {"code":"pg.conn.total","label":"当前连接数","unit":"","color":"#15A36A"},
      {"code":"host.availability","label":"关联主机可达性","unit":"","color":"#E08600"}]'::jsonb,
    '[{"cause":"PostgreSQL 进程停止或启动失败","confidence":0.75,"color":"danger","evidence":["检查 postmaster/postgres 进程、systemd 状态和数据库日志","主机仍可达但 pg.availability=0 时优先定位数据库进程与监听端口"]},
      {"cause":"监听、网络或认证配置异常","confidence":0.65,"color":"warning","evidence":["检查 listen_addresses、端口、防火墙、pg_hba.conf 与监控账号权限","结合采集日志区分连接拒绝、超时和认证失败"]},
      {"cause":"关联主机或 exporter 不可达","confidence":0.45,"color":"info","evidence":["host.availability 同时为 0 时，先处理主机或网络故障","仅 exporter 异常不一定代表 PostgreSQL 不可用"]}]'::jsonb,
    '[{"title":"确认 PG 进程与端口","description":"检查 PostgreSQL 服务、监听端口和数据库日志，区分进程故障与网络/认证问题","action":"查看 PG 实时概况","link":"pg_realtime"},
      {"title":"查看采集失败详情","description":"根据采集日志中的拒绝、超时或认证错误定位连接链路","action":"查看采集任务","link":"collector"},
      {"title":"按 PG 可用性手册排查","description":"结合实例和主机可达性确定故障层级","action":"查看知识库","link":"knowledge"}]'::jsonb,
    '[{"action":"验证 PostgreSQL 服务与监听","risk":"low","description":"在数据库主机只读检查进程和端口","sql":"systemctl status postgresql\nss -lntp | grep 5432","impact":"只读检查，无风险"},
      {"action":"验证数据库连接","risk":"low","description":"使用监控账号从采集节点验证连接链路","sql":"psql -h <host> -p <port> -U <monitor_user> -d <database> -c ''SELECT version();''","impact":"只读连接验证，无风险"}]'::jsonb,
    TRUE, TRUE, 5, 'PG 实例连接失败与可达性告警'
)
ON CONFLICT (profile_code) DO UPDATE SET
    profile_label = EXCLUDED.profile_label, db_type = EXCLUDED.db_type,
    match_rules = EXCLUDED.match_rules, related_metrics = EXCLUDED.related_metrics,
    causes = EXCLUDED.causes, steps = EXCLUDED.steps, actions = EXCLUDED.actions,
    enabled = TRUE, updated_at = now();

INSERT INTO alert_drilldown_profile
    (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions,
     builtin, enabled, sort, remark)
VALUES (
    'pg_host_availability', 'PostgreSQL 关联主机可达类', 'postgresql',
    '[{"matchType":"exact","pattern":"host.availability"},{"matchType":"exact","pattern":"host.uptime"}]'::jsonb,
    '[{"code":"host.availability","label":"主机可达性","unit":"","color":"#E5484D"},
      {"code":"host.uptime","label":"主机运行时长","unit":"s","color":"#0C7C97"},
      {"code":"pg.availability","label":"PG 实例可达性","unit":"","color":"#15A36A"},
      {"code":"pg.tps","label":"PG TPS","unit":"","color":"#E08600"}]'::jsonb,
    '[{"cause":"node_exporter/windows_exporter 服务异常","confidence":0.7,"color":"warning","evidence":["PG 指标仍更新但 host.* 中断时，说明数据库存活、主机采集链路异常","检查 exporter 进程、端口和防火墙"]},
      {"cause":"主机宕机或网络中断","confidence":0.65,"color":"danger","evidence":["PG 可达性和主机指标同时中断时，主机整体故障可能性高","检查 SSH、网络与基础设施告警"]}]'::jsonb,
    '[{"title":"区分主机故障与 exporter 故障","description":"对照 PG 可达性和 TPS 是否继续更新","action":"查看 PG 实时概况","link":"pg_realtime"},
      {"title":"检查 exporter 与网络","description":"验证 exporter 服务、端口和主机网络","action":"查看采集任务","link":"collector"}]'::jsonb,
    '[{"action":"验证 exporter 端点","risk":"low","description":"从采集服务器验证主机指标端点","sql":"curl -s http://<host>:9100/metrics | head","impact":"只读检查，无风险"}]'::jsonb,
    TRUE, TRUE, 6, 'PG 实例关联主机的 exporter/主机可达告警'
)
ON CONFLICT (profile_code) DO UPDATE SET
    profile_label = EXCLUDED.profile_label, db_type = EXCLUDED.db_type,
    match_rules = EXCLUDED.match_rules, related_metrics = EXCLUDED.related_metrics,
    causes = EXCLUDED.causes, steps = EXCLUDED.steps, actions = EXCLUDED.actions,
    enabled = TRUE, updated_at = now();

INSERT INTO alert_drilldown_profile
    (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions,
     builtin, enabled, sort, remark)
VALUES (
    'pg_host_resource', 'PostgreSQL 关联主机资源类', 'postgresql',
    '[{"matchType":"prefix","pattern":"host."}]'::jsonb,
    '[{"code":"host.cpu.usage","label":"主机 CPU 使用率","unit":"%","color":"#E5484D"},
      {"code":"host.mem.usage","label":"主机内存使用率","unit":"%","color":"#E08600"},
      {"code":"host.disk.usage_max","label":"磁盘使用率","unit":"%","color":"#9B59B6"},
      {"code":"host.diskio.util_max","label":"磁盘 IO 繁忙度","unit":"%","color":"#6366F1"},
      {"code":"pg.tps","label":"PG TPS","unit":"","color":"#15A36A"},
      {"code":"pg.conn.active","label":"PG 活跃连接数","unit":"","color":"#0C7C97"}]'::jsonb,
    '[{"cause":"PostgreSQL 工作负载消耗主机资源","confidence":0.7,"color":"danger","evidence":["TPS、活跃连接与主机资源同步上升时，优先排查数据库内部负载","结合 pg_stat_activity 与 pg_stat_statements 定位高耗语句"]},
      {"cause":"主机其他进程争抢资源","confidence":0.6,"color":"warning","evidence":["PG 负载平稳但主机资源升高时，检查备份、压缩、杀毒或混部应用","按进程查看 CPU、内存和 IO 大户"]},
      {"cause":"容量或规格不足","confidence":0.45,"color":"info","evidence":["资源长期缓慢爬升且无突发拐点时，按趋势评估扩容","避免只提高告警阈值掩盖容量问题"]}]'::jsonb,
    '[{"title":"对照主机与 PG 负载","description":"比较 CPU、内存、磁盘、IO 与 TPS、活跃连接的变化时间","action":"查看 PG 实时概况","link":"pg_realtime"},
      {"title":"定位 PG 内部高耗语句","description":"结合 Top SQL、等待事件和活动会话确认数据库内因","action":"前往 PG 性能分析","link":"pg_performance"},
      {"title":"检查主机其他进程","description":"登录主机按 CPU、内存和 IO 排序确认资源归属","action":"查看知识库","link":"knowledge"}]'::jsonb,
    '[{"action":"查看主机资源大户","risk":"low","description":"只读确认 postgres 与其他进程的资源占用","sql":"top -c -o %CPU\nps aux --sort=-rss | head -20\niotop -oPa","impact":"只读检查，无风险"},
      {"action":"查看 PG 活跃会话","risk":"low","description":"按运行时长查看当前活跃 SQL","sql":"SELECT pid, usename, application_name, now()-query_start AS duration, wait_event_type, wait_event, left(query,200) FROM pg_stat_activity WHERE state=''active'' ORDER BY query_start;","impact":"只读查询，无风险"}]'::jsonb,
    TRUE, TRUE, 8, 'PG 实例关联主机的 CPU/内存/Swap/磁盘IO/网络类告警'
)
ON CONFLICT (profile_code) DO UPDATE SET
    profile_label = EXCLUDED.profile_label, db_type = EXCLUDED.db_type,
    match_rules = EXCLUDED.match_rules, related_metrics = EXCLUDED.related_metrics,
    causes = EXCLUDED.causes, steps = EXCLUDED.steps, actions = EXCLUDED.actions,
    enabled = TRUE, updated_at = now();

-- 临时文件落盘场景应精确命中缓存与吞吐画像，而非 PG 通用画像。
UPDATE alert_drilldown_profile
SET match_rules = match_rules ||
        '[{"matchType":"exact","pattern":"scenario.pg_temp_spill"}]'::jsonb,
    updated_at = now()
WHERE profile_code = 'pg_cache_perf'
  AND db_type = 'postgresql'
  AND NOT match_rules @>
        '[{"matchType":"exact","pattern":"scenario.pg_temp_spill"}]'::jsonb;

-- 数据库定义是采集频率的唯一事实来源；为全部 PG 画像补齐 1m/1h，避免前端猜测 MySQL 编码。
UPDATE alert_drilldown_profile p
SET related_metrics = (
        SELECT COALESCE(jsonb_agg(
            CASE WHEN md.frequency IN ('1m', '1h')
                 THEN item.metric || jsonb_build_object('frequency', md.frequency)
                 ELSE item.metric END
            ORDER BY item.ord), '[]'::jsonb)
        FROM jsonb_array_elements(p.related_metrics) WITH ORDINALITY AS item(metric, ord)
        LEFT JOIN metric_definition md ON md.metric_code = item.metric ->> 'code'
    ),
    updated_at = now()
WHERE p.db_type = 'postgresql';
