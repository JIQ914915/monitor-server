package com.lzzh.monitor.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 告警通知通道配置。 */
@Data
@ConfigurationProperties(prefix = "alert.notify")
public class AlertNotifyProperties {

    private Retry retry = new Retry();
    private Email email = new Email();
    private Sms sms = new Sms();
    private Async async = new Async();
    private Storm storm = new Storm();

    /** 通知记录保留天数（终态记录超期清理，防止表无限增长）。 */
    private int retentionDays = 30;
    /** 单次清理删除上限，避免长事务。 */
    private int cleanupBatchSize = 2000;
    /**
     * 规则未显式配置 silencePeriod 时的默认重复通知静默期（分钟），
     * 防止持续异常时每个扫描周期都重发通知；规则显式配置 0 表示每次触发都通知。
     */
    private int defaultSilencePeriodMinutes = 30;

    @Data
    public static class Retry {
        private int maxRetry = 3;
        private int batchSize = 50;
        private int backoffSeconds = 300;
        /** sending 态超过该分钟数未更新视为认领方崩溃，可被重新认领。 */
        private int staleSendingMinutes = 10;
    }

    /**
     * 告警风暴抑制：同一实例在窗口内通知数超过阈值后，抑制逐条通知并按间隔发送聚合摘要，
     * 避免大量规则同时触发造成通知轰炸。{@code threshold<=0} 关闭。
     */
    @Data
    public static class Storm {
        /** 滑动窗口（分钟）。 */
        private int windowMinutes = 5;
        /** 窗口内每实例允许的逐条通知数上限；超过后进入抑制。 */
        private int threshold = 10;
        /** 抑制期间聚合摘要通知的最小发送间隔（分钟）。 */
        private int digestIntervalMinutes = 10;
    }

    /**
     * 通知异步发送线程池：将 Webhook/邮件/短信等慢发送从告警评估主循环剥离，
     * 避免下游超时阻塞评估线程。队列满时回退为调用线程发送以形成背压。
     */
    @Data
    public static class Async {
        /** 发送线程数。 */
        private int poolSize = 4;
        /** 待发送队列容量；满后由评估线程直接发送（背压）。 */
        private int queueCapacity = 500;
    }

    @Data
    public static class Email {
        private String provider = "default";
        private boolean enabled = false;
        private String from;
    }

    /**
     * 短信通道：provider 路由到对应 SmsProvider 实现（aliyun=阿里云 SDK，http=通用 HTTP 网关）。
     * 项目化更换供应商仅需改配置或新增一个 SmsProvider 实现，不动通知主流程。
     */
    @Data
    public static class Sms {
        private String provider = "aliyun";
        private boolean enabled = false;
        private Aliyun aliyun = new Aliyun();
        private HttpSms http = new HttpSms();
    }

    @Data
    public static class Aliyun {
        private String accessKeyId;
        private String accessKeySecret;
        private String endpoint = "dysmsapi.aliyuncs.com";
        private String signName;
        private String templateCode;
    }

    /** 通用 HTTP 短信网关配置（自建/集成商短信平台）。 */
    @Data
    public static class HttpSms {
        /** 网关发送接口 URL（POST）。 */
        private String url;
        /** 附加请求头（如 Authorization）。 */
        private java.util.Map<String, String> headers;
        /**
         * 请求体模板，占位符：{phone} {message} {ruleName} {instanceName} {ruleLevel} {kind}。
         * 例：{"mobile":"{phone}","content":"[数据库监控] {ruleName}: {message}"}
         */
        private String bodyTemplate;
        /** Content-Type，默认 application/json。 */
        private String contentType = "application/json";
        /** 成功判定关键字（HTTP 2xx 且响应体包含该子串才算成功；留空只看状态码）。 */
        private String successKeyword;
        /** 请求超时（秒）。 */
        private int timeoutSeconds = 8;
    }
}
