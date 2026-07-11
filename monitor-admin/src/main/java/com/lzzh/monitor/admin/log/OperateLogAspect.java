package com.lzzh.monitor.admin.log;

import com.lzzh.monitor.admin.security.LoginUser;
import com.lzzh.monitor.admin.security.SecurityUtils;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.dao.entity.SysOperLog;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code @OperateLog} 落库切面。
 *
 * <p>改造要点（相对于原同步写入版本）：
 * <ol>
 *   <li>构建 {@link SysOperLog} 实体后发布 {@link OperateLogEvent} Spring 事件，
 *       由 {@link OperateLogEventListener} 在独立线程池中异步 INSERT，
 *       HTTP 请求线程不再等待 DB 写入完成。</li>
 *   <li>detail 字段写入前做敏感字段脱敏：将 JSON 字符串中 password/secret/token
 *       等字段的值替换为 {@code "***"}，防止明文密码出现在审计日志中。</li>
 * </ol>
 */
@Aspect
@Component
public class OperateLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperateLogAspect.class);

    /**
     * 需要脱敏的字段名关键片段（子串匹配，忽略大小写）。
     *
     * <p>此前实现要求字段名与关键词"完全相等"，导致 {@code dingtalkSecret}/{@code feishuSecret}/
     * {@code connPassword}/{@code accessKeySecret} 等复合命名的敏感字段完全绕过脱敏、明文写入审计日志。
     * 改为"字段名包含该关键片段即脱敏"，覆盖任意前后缀组合的敏感字段命名。
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "secret", "token", "accesstoken",
            "refreshtoken", "privatekey", "credential"
    );

    /** 匹配 JSON 中形如 "xxxFieldName":"value" 的模式，字段名只需包含 {@link #SENSITIVE_KEYS} 任一片段。 */
    private static final Pattern SENSITIVE_PATTERN = buildSensitivePattern();

    private final ApplicationEventPublisher eventPublisher;

    public OperateLogAspect(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("@annotation(operateLog)")
    public Object around(ProceedingJoinPoint pjp, OperateLog operateLog) throws Throwable {
        boolean success = true;
        String detail = "操作成功";
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            success = false;
            detail = ex.getMessage();
            throw ex;
        } finally {
            try {
                publishEvent(operateLog, success, detail);
            } catch (Exception e) {
                // 事件发布失败不影响主流程（极低概率）
                log.warn("发布操作日志事件失败: {}", e.getMessage());
            }
        }
    }

    private void publishEvent(OperateLog operateLog, boolean success, String detail) {
        SysOperLog entity = new SysOperLog();
        entity.setOperTime(LocalDateTime.now());
        entity.setUsername(currentUsername());
        entity.setModule(operateLog.module());
        entity.setAction(operateLog.action());
        entity.setIp(currentIp());
        entity.setSuccess(success);
        entity.setDetail(truncate(maskSensitive(detail), 1000));
        eventPublisher.publishEvent(new OperateLogEvent(this, entity));
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────

    private String currentUsername() {
        try {
            LoginUser u = SecurityUtils.current();
            return u.username();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * 将字符串中敏感字段的值替换为 {@code "***"}。
     * 仅处理 JSON 格式的 detail 字段；非 JSON 内容原样返回。
     */
    static String maskSensitive(String text) {
        if (text == null || text.isBlank()) return text;
        return SENSITIVE_PATTERN.matcher(text).replaceAll("\"$1\":\"***\"");
    }

    private static Pattern buildSensitivePattern() {
        // 构造 (?i)"([^"]*(password|passwd|secret|...)[^"]*)"\s*:\s*"[^"]*"
        // 字段名只需"包含"任一敏感片段即命中，覆盖 dingtalkSecret/connPassword 等复合命名。
        String keys = String.join("|", SENSITIVE_KEYS.stream()
                .map(Pattern::quote)
                .toList());
        return Pattern.compile(
                "(?i)\"([^\"]*(?:" + keys + ")[^\"]*)\"\\s*:\\s*\"[^\"]*\"",
                Pattern.CASE_INSENSITIVE
        );
    }
}
