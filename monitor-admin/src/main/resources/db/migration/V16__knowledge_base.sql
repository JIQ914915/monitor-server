-- =============================================================
-- 知识库模块：知识文章表 + 系统菜单 + 预设角色写权限
--   正文以富文本 HTML 存储（content），分类/标签用于左侧导航与检索
-- =============================================================

CREATE TABLE knowledge_article (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    category    VARCHAR(32)  NOT NULL,                     -- fault/performance/practice/mysql/postgresql/backup
    tags        JSONB        NOT NULL DEFAULT '[]'::jsonb, -- 标签集合
    content     TEXT,                                       -- 富文本正文（HTML）
    author      VARCHAR(64),
    views       INT          NOT NULL DEFAULT 0,
    likes       INT          NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE knowledge_article IS '知识库文章（富文本正文，按分类/标签组织，全部角色可读、管理员/DBA可写）';
CREATE INDEX idx_kb_category ON knowledge_article (category);

-- 种子文章
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes) VALUES
  ('MySQL 慢查询优化指南', 'performance', '["MySQL","性能优化","慢查询"]'::jsonb,
   '<h2>概述</h2><p>慢查询是数据库性能问题的常见来源。本文介绍定位与优化的通用方法。</p><h3>定位手段</h3><ul><li>开启慢查询日志（<code>slow_query_log</code>），设置合理的 <code>long_query_time</code>。</li><li>使用 <code>EXPLAIN</code> 分析执行计划，关注全表扫描与临时表。</li></ul><h3>优化建议</h3><ol><li>为高频过滤字段建立合适索引，避免索引失效（隐式类型转换、函数包裹列）。</li><li>避免 <code>SELECT *</code>，只取所需列。</li><li>大结果集分页时使用游标或延迟关联。</li></ol>',
   '张三', 1280, 96),
  ('PostgreSQL 索引最佳实践', 'postgresql', '["PostgreSQL","索引"]'::jsonb,
   '<h2>索引类型选择</h2><p>PostgreSQL 提供 B-Tree、Hash、GIN、GiST、BRIN 等索引类型，应按查询模式选择。</p><ul><li><strong>B-Tree</strong>：等值与范围查询的默认选择。</li><li><strong>GIN</strong>：适用于数组、JSONB、全文检索。</li><li><strong>BRIN</strong>：适用于物理有序的大表（如时间序列）。</li></ul><p>定期使用 <code>REINDEX</code> 与 <code>ANALYZE</code> 维护统计信息。</p>',
   '李四', 860, 54),
  ('主从复制延迟排查方法', 'fault', '["复制","故障诊断"]'::jsonb,
   '<h2>常见原因</h2><ol><li>从库单线程回放（MySQL 5.6 前），可开启并行复制缓解。</li><li>大事务或长事务导致回放阻塞。</li><li>从库硬件/IO 能力不足。</li></ol><h2>排查步骤</h2><p>查看 <code>SHOW REPLICA STATUS</code> 中的 <code>Seconds_Behind_Master</code>，结合 relay log 回放位点判断瓶颈环节。</p>',
   '王五', 642, 38),
  ('数据库备份策略详解', 'backup', '["备份恢复","运维"]'::jsonb,
   '<h2>备份分层</h2><p>建议采用「全量 + 增量 + binlog」组合，兼顾恢复点目标（RPO）与恢复时间目标（RTO）。</p><ul><li>物理备份：xtrabackup / pg_basebackup。</li><li>逻辑备份：mysqldump / pg_dump，便于跨版本迁移。</li></ul><p>务必定期做<strong>恢复演练</strong>，未经验证的备份等于没有备份。</p>',
   '张三', 991, 71),
  ('高可用架构设计方案', 'practice', '["高可用","架构","最佳实践"]'::jsonb,
   '<h2>目标</h2><p>消除单点故障，保证故障时快速切换且数据不丢失。</p><h2>常见方案</h2><ul><li>MySQL：MHA / MGR / Orchestrator。</li><li>PostgreSQL：Patroni + etcd + HAProxy。</li></ul><p>结合 VIP 或服务发现实现应用无感切换。</p>',
   '李四', 1530, 120);

-- 系统菜单：知识库（归入「系统设置」分组，路由 /system/knowledge，组件 system/knowledge）
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('知识库', 'knowledge', 'menu', '系统级', 'Collection', 'knowledge', 'system/knowledge', 'knowledge', 16, 'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'system'), '数据库运维知识库',
        '[{"name":"新增","code":"knowledge:create","status":"enabled"},{"name":"编辑","code":"knowledge:update","status":"enabled"},{"name":"删除","code":"knowledge:delete","status":"enabled"}]'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- 预设角色：DBA 具备知识库读写（需求 §11.11 知识库 管理员/DBA:W）
UPDATE sys_role
   SET permissions = (permissions::jsonb
        || '["knowledge","knowledge:create","knowledge:update","knowledge:delete"]'::jsonb)
 WHERE code = 'dba';
