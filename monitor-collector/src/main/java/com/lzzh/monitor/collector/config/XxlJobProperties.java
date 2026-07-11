package com.lzzh.monitor.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** xxl-job 执行器配置（application.yml: xxl.job.*）。 */
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    /** 调度中心地址。 */
    private String adminAddresses = "http://localhost:8081/xxl-job-admin";

    /** 执行器通讯 token。 */
    private String accessToken = "";

    /** 执行器 AppName（在调度中心登记）。 */
    private String appname = "monitor-collector";

    /** 执行器端口。 */
    private int port = 9999;

    /** 日志路径与保留天数。 */
    private String logPath = "./logs/xxl-job";
    private int logRetentionDays = 30;

    public String getAdminAddresses() {
        return adminAddresses;
    }

    public void setAdminAddresses(String adminAddresses) {
        this.adminAddresses = adminAddresses;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public int getLogRetentionDays() {
        return logRetentionDays;
    }

    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }
}
