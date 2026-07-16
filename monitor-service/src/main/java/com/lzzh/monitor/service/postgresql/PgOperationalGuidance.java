package com.lzzh.monitor.service.postgresql;

record PgOperationalGuidance(String conclusion, String possibleCause, String impact, String action) {
    static PgOperationalGuidance resolve(String category, String eventType, String severity) {
        String type = eventType == null ? "" : eventType;
        if (type.endsWith("_unavailable")) {
            return new PgOperationalGuidance("该项监控数据暂不可用", "版本不支持、账号权限不足或本次采集失败", "本项状态无法判断，不能据此认定为正常", "查看事件详情中的 reason，按提示补充只读权限或确认版本支持范围");
        }
        return switch (type) {
            case "replication_slot" -> new PgOperationalGuidance("复制槽存在 WAL 积压风险", "订阅端停止消费、网络中断或复制槽已废弃", "WAL 文件可能持续占用磁盘并影响数据库可用性", "确认消费端状态；无用复制槽经 DBA 评审后人工清理");
            case "physical_sender", "wal_receiver" -> new PgOperationalGuidance("物理复制传输或回放状态异常", "主备网络异常、备库负载过高或 WAL 获取中断", "备库数据新鲜度下降，故障切换的数据风险增大", "依次检查发送、接收、回放状态及槽积压，再检查网络和备库资源");
            case "subscription", "subscription_table_sync", "subscription_conflict_stats" -> new PgOperationalGuidance("逻辑复制存在异常或同步未完成", "订阅 worker 未运行、表同步停滞或数据冲突", "订阅端数据延迟或不一致", "检查订阅 worker、表同步状态、错误计数和最近冲突对象");
            case "recovery_conflicts" -> new PgOperationalGuidance("备库查询与恢复过程发生冲突", "长查询阻止 WAL 回放或备库冲突参数不合适", "查询可能被取消，复制回放也可能继续延迟", "定位冲突数据库和长查询，结合业务窗口人工调整查询或参数");
            case "wal_archiver" -> new PgOperationalGuidance("最近一次 WAL 归档失败", "归档命令、目标目录权限、网络或空间异常", "WAL 可能堆积，时间点恢复链路存在风险", "核对失败次数增量、最近失败 WAL 和最近成功时间，再检查归档目标");
            case "vacuum", "analyze", "create_index", "cluster", "copy", "base_backup" -> new PgOperationalGuidance(
                    "数据库运维任务长时间没有进展", "任务被锁阻塞、I/O 饱和或目标对象竞争", "维护窗口可能超时，并持续占用数据库资源", "查看 blocked_by 和根阻塞会话，确认资源压力后由 DBA 人工处置");
            default -> new PgOperationalGuidance(
                    "warning".equals(severity) || "critical".equals(severity) ? "发现需要关注的 PostgreSQL 运维风险" : "该项 PostgreSQL 运维状态正常",
                    "请结合事件证据和同时间段指标判断", "可能影响相关数据库对象或任务", "打开事件详情，按时间、对象和指纹继续排查");
        };
    }
}
