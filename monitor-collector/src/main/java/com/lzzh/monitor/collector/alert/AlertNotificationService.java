package com.lzzh.monitor.collector.alert;

import cn.hutool.json.JSONUtil;
import com.lzzh.monitor.collector.alert.sms.SmsProvider;
import com.lzzh.monitor.collector.config.AlertNotifyProperties;
import com.lzzh.monitor.common.security.PasswordCipher;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertNotifyChannelConfig;
import com.lzzh.monitor.dao.entity.AlertNotifyRecord;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.dao.mapper.AlertNotifyChannelConfigMapper;
import com.lzzh.monitor.dao.mapper.AlertNotifyRecordMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警通知发送与失败重试。
 *
 * <p><b>异步发送（P0）</b>：{@link #notifyOnTrigger}/{@link #notifyOnRecovery} 在告警评估主循环中被调用，
 * 为避免 Webhook/邮件/短信下游超时阻塞评估线程，方法内只做"解析通道联系人 + 落库 pending 记录"等快操作，
 * 真正的网络发送提交到 {@link #sendExecutor} 异步执行；返回值表示"是否已派发（调度成功）"，
 * 发送失败由 {@link #retryFailed} 依据 next_retry_time 重试。
 */
@Service
public class AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationService.class);

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** payload 中承载机器人签名密钥的私有字段；不落入 {@code AlertNotifyRecordVo}，不对外暴露。 */
    private static final String SECRET_PAYLOAD_KEY = "__secret";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final AlertNotifyRecordMapper notifyRecordMapper;
    private final AlertNotifyChannelConfigMapper channelConfigMapper;
    private final AlertNotifyProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AlertContactResolver contactResolver;
    private final AlertStormGuard stormGuard;
    private final PasswordCipher passwordCipher;
    /** 短信供应商 SPI：按 alert.notify.sms.provider 路由（aliyun / http / 项目自定义实现）。 */
    private final Map<String, SmsProvider> smsProviders;
    /** 通知发送线程池：慢发送在此异步执行，不阻塞评估主循环。 */
    private final ExecutorService sendExecutor;

    public AlertNotificationService(AlertNotifyRecordMapper notifyRecordMapper,
                                    AlertNotifyChannelConfigMapper channelConfigMapper,
                                    AlertNotifyProperties properties,
                                    ObjectProvider<JavaMailSender> mailSenderProvider,
                                    AlertContactResolver contactResolver,
                                    AlertStormGuard stormGuard,
                                    PasswordCipher passwordCipher,
                                    List<SmsProvider> smsProviderList) {
        this.notifyRecordMapper = notifyRecordMapper;
        this.channelConfigMapper = channelConfigMapper;
        this.properties = properties;
        this.mailSenderProvider = mailSenderProvider;
        this.contactResolver = contactResolver;
        this.stormGuard = stormGuard;
        this.passwordCipher = passwordCipher;
        Map<String, SmsProvider> providers = new LinkedHashMap<>();
        for (SmsProvider p : smsProviderList) {
            providers.put(p.code().toLowerCase(java.util.Locale.ROOT), p);
        }
        this.smsProviders = providers;
        int poolSize = Math.max(1, properties.getAsync().getPoolSize());
        int queueCapacity = Math.max(1, properties.getAsync().getQueueCapacity());
        this.sendExecutor = new ThreadPoolExecutor(
                poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                namedThreadFactory("alert-notify-"),
                // 队列满则由调用线程直接发送，形成背压而非丢弃通知
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    public void shutdown() {
        sendExecutor.shutdown();
        try {
            if (!sendExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                sendExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sendExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefix + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * 触发通知。
     *
     * <p><b>语义澄清</b>：返回 true 仅表示"至少一个启用通道已成功派发"（通知记录已落库为
     * {@code pending} 并提交异步发送/重试队列），<b>不代表下游真正送达成功</b>——网络发送在
     * {@link #sendExecutor} 中异步执行，真实送达结果需查 {@code alert_notify_record.status}
     * （pending/sending/success/failed/dead），失败由 {@link #retryFailed} 依据 next_retry_time 重试。
     * 调用方据此设置的 {@code lastNotifyTime}/{@code notifyCount} 仅用于"重复通知静默期"判断，
     * 同样是"已派发"口径，不是"已确认送达"口径。
     */
    public boolean notifyOnTrigger(AlertRule rule, AlertEvent event) {
        Map<String, Object> cfg = rule.getNotificationConfig();
        if (!bool(cfg, "notifyOnTrigger", true)) {
            return false;
        }
        return notifyByKind(rule, event, "trigger");
    }

    /**
     * 恢复通知。语义同 {@link #notifyOnTrigger}：返回 true 仅表示"已派发"，非"已送达"。
     * 缺省与触发通知一致（默认开），与前端表单默认值同口径；不需要恢复通知的规则显式取消勾选即可。
     */
    public boolean notifyOnRecovery(AlertRule rule, AlertEvent event) {
        Map<String, Object> cfg = rule.getNotificationConfig();
        if (!bool(cfg, "notifyOnRecovery", true)) {
            return false;
        }
        return notifyByKind(rule, event, "recovery");
    }

    /**
     * 失败/滞留通知重试。候选记录通过 {@code FOR UPDATE SKIP LOCKED} 原子认领为 {@code sending}，
     * 多个 collector 并发执行互不重复发送。
     *
     * <p>批次内复用 {@link #sendExecutor} 并行发送：串行发送最坏是 batchSize × 单次超时（50 × 8s ≈ 400s）一轮，
     * 下游大面积故障时重试严重滞后；并行后一轮墙钟时间约为 batchSize / poolSize × 单次超时。
     * 任务线程阻塞等待整批完成再返回成功数：已认领记录处于 {@code sending} 态，等待期间不会被下一轮重复认领；
     * 线程池队列打满时由 {@code CallerRunsPolicy} 回退到任务线程执行，形成天然背压。
     */
    public int retryFailed() {
        List<AlertNotifyRecord> records = notifyRecordMapper.claimRetryCandidates(
                properties.getRetry().getBatchSize(),
                properties.getRetry().getStaleSendingMinutes());
        if (records.isEmpty()) {
            return 0;
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(records.size());
        for (AlertNotifyRecord record : records) {
            futures.add(CompletableFuture.supplyAsync(() -> retryOne(record), sendExecutor)
                    .exceptionally(e -> {
                        log.warn("重试告警通知异常 recordId={} err={}", record.getId(), e.getMessage());
                        return false;
                    }));
        }
        int success = 0;
        for (CompletableFuture<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.join())) {
                success++;
            }
        }
        return success;
    }

    /**
     * 清理超过保留期的终态通知记录，防止表无限增长。由通知重试任务周期性调用。
     *
     * @return 删除行数
     */
    public int cleanupRetired() {
        int retentionDays = Math.max(1, properties.getRetentionDays());
        int batch = Math.max(1, properties.getCleanupBatchSize());
        try {
            return notifyRecordMapper.deleteRetired(retentionDays, batch);
        } catch (Exception e) {
            log.warn("清理过期告警通知记录失败: {}", e.getMessage());
            return 0;
        }
    }

    private boolean notifyByKind(AlertRule rule, AlertEvent event, String kind) {
        Map<String, Object> cfg = rule.getNotificationConfig();

        // 先只读规则上的通道勾选（无 IO），再按需加载：URL 类通道才查全局配置表，
        // 邮件/短信才解析实例联系人（3 次查库），避免风暴/无有效目标时空耗 DB。
        boolean wantEmail = bool(cfg, "channelEmail", false);
        boolean wantSms = bool(cfg, "channelSms", false);
        boolean wantAnyUrl = false;
        for (String channel : List.of("webhook", "dingtalk", "wecom", "feishu")) {
            if (bool(cfg, channelToggleKey(channel), false)) {
                wantAnyUrl = true;
                break;
            }
        }
        if (!wantEmail && !wantSms && !wantAnyUrl) {
            log.debug("规则 [{}] 未启用任何通知通道，跳过通知", rule.getRuleCode());
            return false;
        }

        // 组装本次"确有有效地址/联系人"的可派发目标：风暴抑制计数必须放在确认可派发之后，
        // 否则通道未启用/无地址/无联系人时也会空耗风暴窗口配额与摘要间隔，导致运维实际一条都没收到。
        List<DispatchTarget> targets = new ArrayList<>();
        if (wantAnyUrl) {
            // URL 类通道（webhook/钉钉/企业微信/飞书）的地址与密钥来自全局配置表，规则里只做通道勾选
            Map<String, AlertNotifyChannelConfig> channels = loadChannelConfigs();
            for (String channel : List.of("webhook", "dingtalk", "wecom", "feishu")) {
                if (!bool(cfg, channelToggleKey(channel), false)) {
                    continue;
                }
                AlertNotifyChannelConfig global = channels.get(channel);
                if (global == null || !Boolean.TRUE.equals(global.getEnabled())) {
                    log.warn("规则 [{}] 勾选了 {} 通知但该通道全局配置未启用（系统管理→通知通道），跳过", rule.getRuleCode(), channel);
                    continue;
                }
                List<String> urls = globalUrls(global);
                if (urls.isEmpty()) {
                    log.warn("规则 [{}] 勾选了 {} 通知但全局配置未维护地址，跳过", rule.getRuleCode(), channel);
                    continue;
                }
                String secret = globalSecret(global);
                for (String url : urls) {
                    targets.add(new DispatchTarget(channel, "default", url, secret));
                }
            }
        }
        if (wantEmail || wantSms) {
            AlertContactResolver.ContactTargets contacts = contactResolver.resolve(event.getInstanceId());
            if (wantEmail) {
                for (String to : emailTargets(contacts)) {
                    targets.add(new DispatchTarget("email", properties.getEmail().getProvider(), to, null));
                }
            }
            if (wantSms) {
                for (String phone : smsTargets(contacts)) {
                    targets.add(new DispatchTarget("sms", properties.getSms().getProvider(), phone, null));
                }
            }
        }

        if (targets.isEmpty()) {
            // 勾选了通道但无有效地址/联系人：不消耗风暴抑制配额（此处返回前未调用 decide）
            log.debug("规则 [{}] 已启用通知通道但无有效派发目标，跳过通知", rule.getRuleCode());
            return false;
        }

        // 告警风暴抑制：仅作用于触发通知；恢复通知不受限
        String effectiveKind = kind;
        if ("trigger".equals(kind)) {
            AlertStormGuard.Decision decision = stormGuard.decide(event.getInstanceId());
            switch (decision.kind()) {
                case SUPPRESS -> {
                    log.debug("实例 [{}] 处于告警风暴抑制期，跳过规则 [{}] 逐条通知",
                            event.getInstanceId(), rule.getRuleCode());
                    return false;
                }
                case DIGEST -> {
                    // 用聚合摘要替换逐条通知内容，仍走该规则配置的通道
                    effectiveKind = "storm";
                    event = withStormMessage(event, buildStormMessage(event, decision.suppressedCount()));
                }
                default -> {
                    // ALLOW：正常逐条通知
                }
            }
        }

        Map<String, Object> payload = buildPayload(effectiveKind, rule, event);
        boolean dispatched = false;
        for (DispatchTarget t : targets) {
            // 钉钉/飞书签名密钥在全局配置中已加密存储，直接透传（withEncryptedSecret 对已加密值幂等）；
            // 无密钥通道（webhook/wecom/email/sms）会移除 __secret 字段
            Map<String, Object> chPayload = t.secret() != null ? withEncryptedSecret(payload, t.secret()) : payload;
            dispatched = recordAndDispatch(rule, event, effectiveKind, t.channel(), t.provider(), t.target(), chPayload) || dispatched;
        }
        return dispatched;
    }

    /** 一次通知的单个派发目标（已通过通道开关 + 全局配置/联系人校验）。 */
    private record DispatchTarget(String channel, String provider, String target, String secret) {
    }

    /**
     * 同步落库 pending 通知记录（快操作），随后把网络发送提交到异步线程池执行。
     *
     * @return 是否成功派发（记录已落库并调度发送）；实际发送成功与否由重试任务兜底。
     */
    private boolean recordAndDispatch(AlertRule rule, AlertEvent event, String kind, String channel,
                                      String provider, String target, Map<String, Object> payload) {
        AlertNotifyRecord record = new AlertNotifyRecord();
        record.setEventId(event.getId());
        record.setEventCode(event.getEventCode());
        record.setRuleCode(rule.getRuleCode());
        record.setNotifyKind(kind);
        record.setChannel(channel);
        record.setProvider(provider);
        record.setTarget(target);
        record.setPayload(JSONUtil.toJsonStr(payload));
        record.setStatus("pending");
        record.setRetryCount(0);
        record.setMaxRetry(properties.getRetry().getMaxRetry());
        try {
            notifyRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("写告警通知记录失败 event={} channel={} target={} err={}",
                    event.getEventCode(), channel, target, e.getMessage());
            return false;
        }
        try {
            sendExecutor.execute(() -> {
                try {
                    // 首发前认领：若已被重试任务抢先认领（如队列积压超 2 分钟），跳过以免双发
                    if (notifyRecordMapper.tryClaimPending(record.getId()) <= 0) {
                        log.debug("通知记录 {} 已被其他执行方认领，跳过异步首发", record.getId());
                        return;
                    }
                    sendRecord(record);
                } catch (Exception e) {
                    log.warn("异步发送告警通知失败 recordId={} err={}", record.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            // 线程池提交异常（极端情况）：记录保持 pending，由重试任务兜底发送
            log.warn("提交异步告警通知失败 recordId={} err={}", record.getId(), e.getMessage());
        }
        return true;
    }

    private boolean retryOne(AlertNotifyRecord record) {
        record.setRetryCount((record.getRetryCount() == null ? 0 : record.getRetryCount()) + 1);
        return sendRecord(record);
    }

    @SuppressWarnings("unchecked")
    private boolean sendRecord(AlertNotifyRecord record) {
        Map<String, Object> payload = JSONUtil.toBean(record.getPayload(), Map.class);
        SendResult result = switch (record.getChannel()) {
            case "webhook" -> sendWebhook(record.getTarget(), payload);
            case "email" -> sendEmail(record.getTarget(), payload);
            case "sms" -> sendSms(record.getTarget(), payload);
            case "dingtalk" -> sendDingtalk(record.getTarget(), payload);
            case "wecom" -> sendWecom(record.getTarget(), payload);
            case "feishu" -> sendFeishu(record.getTarget(), payload);
            default -> SendResult.failed("UNKNOWN_CHANNEL", "不支持的通知通道: " + record.getChannel());
        };
        AlertNotifyRecord update = new AlertNotifyRecord();
        update.setId(record.getId());
        update.setResponseCode(result.code());
        update.setResponseBody(truncate(result.body(), 4000));
        update.setErrorMessage(truncate(result.error(), 1000));
        int retryCount = record.getRetryCount() == null ? 0 : record.getRetryCount();
        int maxRetry = record.getMaxRetry() == null ? properties.getRetry().getMaxRetry() : record.getMaxRetry();
        update.setRetryCount(retryCount);
        if (result.success()) {
            update.setStatus("success");
            update.setSentAt(OffsetDateTime.now());
            update.setNextRetryTime(null);
        } else if (retryCount >= maxRetry) {
            // 死信：重试耗尽仍失败，不再重试。显著日志便于外部日志告警系统捕获。
            update.setStatus("dead");
            update.setNextRetryTime(null);
            log.error("告警通知进入死信（重试 {} 次仍失败）event={} rule={} channel={} target={} err={}",
                    retryCount, record.getEventCode(), record.getRuleCode(),
                    record.getChannel(), record.getTarget(), result.error());
        } else {
            update.setStatus("failed");
            update.setNextRetryTime(OffsetDateTime.now().plusSeconds(properties.getRetry().getBackoffSeconds()));
        }
        notifyRecordMapper.updateById(update);
        return result.success();
    }

    private SendResult sendWebhook(String url, Map<String, Object> payload) {
        try {
            byte[] body = JSONUtil.toJsonStr(payload).getBytes(StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                return SendResult.success(String.valueOf(code), resp.body());
            }
            return SendResult.failed(String.valueOf(code), resp.body());
        } catch (Exception e) {
            return SendResult.failed("EXCEPTION", e.getMessage());
        }
    }

    private SendResult sendEmail(String target, Map<String, Object> payload) {
        if (!properties.getEmail().isEnabled()) {
            return SendResult.failed("DISABLED", "邮件通道未启用");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return SendResult.failed("NO_MAIL_SENDER", "未配置 JavaMailSender");
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (StringUtils.hasText(properties.getEmail().getFrom())) {
                msg.setFrom(properties.getEmail().getFrom());
            }
            msg.setTo(target);
            msg.setSubject("数据库监控告警：" + nullSafe((String) payload.get("ruleName")));
            msg.setText(nullSafe((String) payload.get("message")));
            mailSender.send(msg);
            return SendResult.success("OK", "sent");
        } catch (Exception e) {
            return SendResult.failed("EXCEPTION", e.getMessage());
        }
    }

    /** 短信发送：按 provider 配置路由到 SmsProvider SPI，实现供应商可插拔（§14.2）。 */
    private SendResult sendSms(String target, Map<String, Object> payload) {
        if (!properties.getSms().isEnabled()) {
            return SendResult.failed("DISABLED", "短信通道未启用");
        }
        String providerCode = nullSafe(properties.getSms().getProvider()).toLowerCase(java.util.Locale.ROOT);
        SmsProvider provider = smsProviders.get(providerCode);
        if (provider == null) {
            return SendResult.failed("UNKNOWN_PROVIDER",
                    "不支持的短信 provider: " + properties.getSms().getProvider()
                            + "（可用：" + String.join("/", smsProviders.keySet()) + "）");
        }
        SmsProvider.SmsSendResult r = provider.send(target, payload);
        return r.success() ? SendResult.success(r.code(), r.body()) : SendResult.failed(r.code(), r.error());
    }

    /**
     * 钉钉自定义机器人（支持"加签"安全设置）。
     * <p>签名算法：{@code sign = urlEncode(base64(hmacSha256(secret, timestamp+"\n"+secret)))}，
     * 拼接到 webhook URL 的 {@code timestamp}/{@code sign} 查询参数上（钉钉官方文档）。
     */
    private SendResult sendDingtalk(String url, Map<String, Object> payload) {
        try {
            String secret = passwordCipher.decrypt((String) payload.get(SECRET_PAYLOAD_KEY));
            String finalUrl = RobotNotifySigner.dingtalkSignedUrl(url, secret, System.currentTimeMillis());
            Map<String, Object> body = Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", buildRobotText(payload)));
            return sendJsonRobot(finalUrl, body);
        } catch (Exception e) {
            return SendResult.failed("EXCEPTION", e.getMessage());
        }
    }

    /** 企业微信群机器人：无需签名，webhook key 已内嵌在 URL 中。 */
    private SendResult sendWecom(String url, Map<String, Object> payload) {
        Map<String, Object> body = Map.of(
                "msgtype", "text",
                "text", Map.of("content", buildRobotText(payload)));
        return sendJsonRobot(url, body);
    }

    /**
     * 飞书自定义机器人（支持"签名校验"安全设置）。签名算法见 {@link RobotNotifySigner#feishuSign}
     * （与钉钉的 HMAC key/message 顺序相反）。
     */
    private SendResult sendFeishu(String url, Map<String, Object> payload) {
        try {
            String secret = passwordCipher.decrypt((String) payload.get(SECRET_PAYLOAD_KEY));
            Map<String, Object> body = new LinkedHashMap<>();
            if (StringUtils.hasText(secret)) {
                long timestampSec = System.currentTimeMillis() / 1000;
                body.put("timestamp", String.valueOf(timestampSec));
                body.put("sign", RobotNotifySigner.feishuSign(secret, timestampSec));
            }
            body.put("msg_type", "text");
            body.put("content", Map.of("text", buildRobotText(payload)));
            return sendJsonRobot(url, body);
        } catch (Exception e) {
            return SendResult.failed("EXCEPTION", e.getMessage());
        }
    }

    /**
     * IM 机器人通用发送：钉钉/企业微信/飞书均返回 HTTP 200 但用 body 内的业务码
     * （{@code errcode}/{@code code}，0 表示成功）表达真实结果，因此需要同时校验 HTTP 状态与业务码。
     */
    private SendResult sendJsonRobot(String url, Map<String, Object> body) {
        try {
            byte[] bytes = JSONUtil.toJsonStr(body).getBytes(StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            String respBody = resp.body();
            if (code < 200 || code >= 300) {
                return SendResult.failed(String.valueOf(code), respBody);
            }
            String bizCode = extractBizCode(respBody);
            if (bizCode == null || "0".equals(bizCode)) {
                return SendResult.success(String.valueOf(code), respBody);
            }
            return SendResult.failed(bizCode, respBody);
        } catch (Exception e) {
            return SendResult.failed("EXCEPTION", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractBizCode(String respBody) {
        if (!StringUtils.hasText(respBody)) {
            return null;
        }
        try {
            Map<String, Object> map = JSONUtil.toBean(respBody, Map.class);
            String errcode = normalizeBizCode(map.get("errcode"));
            if (errcode != null) {
                return errcode;
            }
            return normalizeBizCode(map.get("code"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 归一化机器人响应的业务码：兼容数值（{@code 0}）与字符串（{@code "0"}）两种形态。
     * 部分平台（含阿里/自建网关）用字符串返回 code，若仅识别 {@link Number} 会把字符串 "非0" 错判为成功。
     */
    private static String normalizeBizCode(Object raw) {
        if (raw instanceof Number n) {
            return String.valueOf(n.intValue());
        }
        if (raw instanceof String s && !s.isBlank()) {
            String trimmed = s.trim();
            try {
                return String.valueOf((long) Double.parseDouble(trimmed));
            } catch (NumberFormatException ignored) {
                return trimmed;
            }
        }
        return null;
    }

    /** IM 机器人文本消息内容：复用 Webhook/邮件同源的告警文案，附加事件阶段前缀。 */
    private String buildRobotText(Map<String, Object> payload) {
        return RobotNotifySigner.buildRobotText(
                (String) payload.get("kind"),
                (String) payload.get("ruleLevel"),
                (String) payload.get("message"));
    }

    /**
     * 返回携带/清除 {@code __secret} 私有字段的 payload 副本，避免多通道共享同一个 payload 实例互相污染。
     *
     * <p>该 payload 会随 {@link AlertNotifyRecord#getPayload()} 落库（首发失败需要在重试时原样重建请求），
     * 因此机器人签名密钥在写入前用 {@link PasswordCipher} 加密，避免明文密钥落入数据库；
     * {@link #sendDingtalk}/{@link #sendFeishu} 使用前对称解密。
     */
    private Map<String, Object> withEncryptedSecret(Map<String, Object> payload, String secret) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        if (StringUtils.hasText(secret)) {
            copy.put(SECRET_PAYLOAD_KEY, passwordCipher.encrypt(secret));
        } else {
            copy.remove(SECRET_PAYLOAD_KEY);
        }
        return copy;
    }

    /** 加载全部通道全局配置（表仅 4 行，通知触发频率受静默期/风暴抑制约束，直查开销可忽略）。 */
    private Map<String, AlertNotifyChannelConfig> loadChannelConfigs() {
        Map<String, AlertNotifyChannelConfig> result = new LinkedHashMap<>();
        try {
            for (AlertNotifyChannelConfig c : channelConfigMapper.selectList(null)) {
                result.put(c.getChannel(), c);
            }
        } catch (Exception e) {
            log.warn("加载通知通道全局配置失败: {}", e.getMessage());
        }
        return result;
    }

    /** 通道名 → 规则通知配置中的勾选键。 */
    private static String channelToggleKey(String channel) {
        return switch (channel) {
            case "webhook" -> "channelWebhook";
            case "dingtalk" -> "channelDingtalk";
            case "wecom" -> "channelWecom";
            case "feishu" -> "channelFeishu";
            default -> throw new IllegalArgumentException("未知通道: " + channel);
        };
    }

    /** 从全局通道配置中提取地址列表。 */
    private static List<String> globalUrls(AlertNotifyChannelConfig global) {
        Object raw = global.getConfig() == null ? null : global.getConfig().get("urls");
        List<String> urls = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s && !s.isBlank()) {
                    urls.add(s.trim());
                }
            }
        }
        return urls.stream().distinct().toList();
    }

    /** 从全局通道配置中提取签名密钥（加密态原样返回，发送时解密）。 */
    private static String globalSecret(AlertNotifyChannelConfig global) {
        Object raw = global.getConfig() == null ? null : global.getConfig().get("secret");
        return raw instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    /** 生成风暴聚合摘要文案。 */
    private String buildStormMessage(AlertEvent event, int suppressedCount) {
        int windowMinutes = Math.max(1, properties.getStorm().getWindowMinutes());
        return "【告警风暴】实例[" + nullSafe(event.getInstanceName()) + "]近 " + windowMinutes
                + " 分钟内触发大量告警，已抑制约 " + suppressedCount
                + " 条逐条通知，请登录监控平台查看并处理。";
    }

    /** 构造带风暴摘要文案的事件快照，仅用于通知渲染，不落库。 */
    private static AlertEvent withStormMessage(AlertEvent event, String stormMessage) {
        AlertEvent snapshot = new AlertEvent();
        snapshot.setId(event.getId());
        snapshot.setEventCode(event.getEventCode());
        snapshot.setRuleId(event.getRuleId());
        snapshot.setRuleName(event.getRuleName());
        snapshot.setRuleLevel(event.getRuleLevel());
        snapshot.setInstanceId(event.getInstanceId());
        snapshot.setInstanceName(event.getInstanceName());
        snapshot.setStatus(event.getStatus());
        snapshot.setTriggerValue(event.getTriggerValue());
        snapshot.setThresholdValue(event.getThresholdValue());
        snapshot.setTriggerTime(event.getTriggerTime());
        snapshot.setLastTriggerTime(event.getLastTriggerTime());
        snapshot.setTriggerCount(event.getTriggerCount());
        snapshot.setAlertMessage(stormMessage);
        return snapshot;
    }

    private static boolean bool(Map<String, Object> cfg, String key, boolean def) {
        if (cfg == null) return def;
        Object v = cfg.get(key);
        return v instanceof Boolean b ? b : def;
    }

    private Map<String, Object> buildPayload(String kind, AlertRule rule, AlertEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind);
        payload.put("ruleId", rule.getId());
        payload.put("ruleCode", nullSafe(rule.getRuleCode()));
        payload.put("ruleName", nullSafe(rule.getRuleName()));
        payload.put("ruleLevel", nullSafe(rule.getRuleLevel()));
        payload.put("eventId", event.getId());
        payload.put("eventCode", nullSafe(event.getEventCode()));
        payload.put("status", nullSafe(event.getStatus()));
        payload.put("instanceId", event.getInstanceId());
        payload.put("instanceName", nullSafe(event.getInstanceName()));
        payload.put("triggerValue", nullSafe(event.getTriggerValue()));
        payload.put("thresholdValue", nullSafe(event.getThresholdValue()));
        payload.put("message", nullSafe(resolveNotifyMessage(event, kind)));
        payload.put("triggerTime", event.getTriggerTime() == null ? null : event.getTriggerTime().toString());
        payload.put("lastTriggerTime", event.getLastTriggerTime() == null ? null : event.getLastTriggerTime().toString());
        payload.put("recoveryTime", event.getRecoveryTime() == null ? null : event.getRecoveryTime().toString());
        payload.put("triggerCount", event.getTriggerCount());
        return payload;
    }

    private List<String> emailTargets(AlertContactResolver.ContactTargets contacts) {
        List<String> targets = contacts.emails();
        if (targets.isEmpty()) {
            log.warn("启用了邮件通知但实例负责人/分组联系人未配置邮箱，跳过邮件发送");
            return Collections.emptyList();
        }
        return targets;
    }

    private List<String> smsTargets(AlertContactResolver.ContactTargets contacts) {
        List<String> targets = contacts.phones();
        if (targets.isEmpty()) {
            log.warn("启用了短信通知但实例负责人/分组联系人未配置手机号，跳过短信发送");
            return Collections.emptyList();
        }
        return targets;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private static String resolveNotifyMessage(AlertEvent event, String kind) {
        String base = resolveEventMessage(event);
        OffsetDateTime ts = "recovery".equals(kind)
                ? (event.getRecoveryTime() != null ? event.getRecoveryTime() : event.getLastTriggerTime())
                : (event.getLastTriggerTime() != null ? event.getLastTriggerTime() : event.getTriggerTime());
        String time = ts == null ? "" : ts.format(FMT);
        return "[" + time + "]-实例[" + nullSafe(event.getInstanceName()) + "]，" + base;
    }

    private static String resolveEventMessage(AlertEvent event) {
        if (event.getAlertMessage() != null && !event.getAlertMessage().isBlank()) {
            return event.getAlertMessage().trim();
        }
        return "【" + nullSafe(event.getRuleName()) + "】当前值 "
                + nullSafe(event.getTriggerValue()) + "，阈值 " + nullSafe(event.getThresholdValue()) + "，告警触发";
    }

    private record SendResult(boolean success, String code, String body, String error) {
        static SendResult success(String code, String body) {
            return new SendResult(true, code, body, null);
        }

        static SendResult failed(String code, String error) {
            return new SendResult(false, code, null, error);
        }
    }
}
