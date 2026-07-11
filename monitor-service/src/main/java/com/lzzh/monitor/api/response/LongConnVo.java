package com.lzzh.monitor.api.response;

import java.util.List;

/** 长连接明细列表响应 VO。 */
public class LongConnVo {

    /** 实例 ID。 */
    private Long instanceId;

    /** 长连接列表（按持续时间降序）。 */
    private List<ConnRow> connections;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public List<ConnRow> getConnections() { return connections; }
    public void setConnections(List<ConnRow> connections) { this.connections = connections; }

    /** 单个长连接条目。 */
    public static class ConnRow {
        /** MySQL processlist ID。 */
        private long connId;
        /** 连接用户名。 */
        private String connUser;
        /** 来源主机。 */
        private String connHost;
        /** 当前数据库。 */
        private String connDb;
        /** 命令类型（Query / Sleep / …）。 */
        private String command;
        /** 连接已持续秒数。 */
        private int timeSeconds;
        /** 当前状态。 */
        private String state;
        /** 当前执行 SQL（最多 2000 字符）。 */
        private String info;
        /** 采集时间（毫秒时间戳，UTC）。 */
        private long collectTimeMs;

        public long getConnId() { return connId; }
        public void setConnId(long connId) { this.connId = connId; }
        public String getConnUser() { return connUser; }
        public void setConnUser(String connUser) { this.connUser = connUser; }
        public String getConnHost() { return connHost; }
        public void setConnHost(String connHost) { this.connHost = connHost; }
        public String getConnDb() { return connDb; }
        public void setConnDb(String connDb) { this.connDb = connDb; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public int getTimeSeconds() { return timeSeconds; }
        public void setTimeSeconds(int timeSeconds) { this.timeSeconds = timeSeconds; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getInfo() { return info; }
        public void setInfo(String info) { this.info = info; }
        public long getCollectTimeMs() { return collectTimeMs; }
        public void setCollectTimeMs(long collectTimeMs) { this.collectTimeMs = collectTimeMs; }
    }
}
