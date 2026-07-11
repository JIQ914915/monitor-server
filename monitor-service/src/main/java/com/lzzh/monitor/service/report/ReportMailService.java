package com.lzzh.monitor.service.report;

import com.lzzh.monitor.dao.entity.MonitorReport;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 报告邮件推送（§11.9 定时报告分发）。
 * <p>定时报告生成归档后，若任务配置了收件邮箱且系统配置了 {@code spring.mail}，
 * 将报告正文分段渲染为 HTML 邮件推送给收件人；未配置邮件服务时静默跳过（不影响归档）。
 * <p>发件人取 {@code monitor.report.mail-from}，缺省回退 {@code spring.mail.username}。
 */
@Service
public class ReportMailService {

    private static final Logger log = LoggerFactory.getLogger(ReportMailService.class);

    @Resource
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${monitor.report.mail-from:${spring.mail.username:}}")
    private String mailFrom;

    /** 邮件服务是否可用（配置了 spring.mail.host 时自动装配 JavaMailSender）。 */
    public boolean available() {
        return mailSenderProvider.getIfAvailable() != null && StringUtils.hasText(mailFrom);
    }

    /**
     * 推送报告邮件。发送失败仅记日志，不抛出（报告归档为主流程，推送为增值动作）。
     *
     * @return 是否成功发出
     */
    public boolean send(List<String> to, MonitorReport report) {
        if (CollectionUtils.isEmpty(to) || report == null) {
            return false;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || !StringUtils.hasText(mailFrom)) {
            log.info("报告邮件推送跳过（未配置 spring.mail），reportCode={}", report.getReportCode());
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to.toArray(String[]::new));
            helper.setSubject("【数据库监控平台】" + report.getTitle());
            helper.setText(buildHtml(report), true);
            sender.send(message);
            log.info("报告邮件已推送 reportCode={} to={}", report.getReportCode(), to);
            return true;
        } catch (Exception e) {
            log.warn("报告邮件推送失败 reportCode={} to={}: {}", report.getReportCode(), to, e.getMessage());
            return false;
        }
    }

    // ── 正文渲染（与前端预览页同一分段结构） ─────────────────────────────────

    @SuppressWarnings("unchecked")
    private String buildHtml(MonitorReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:'Microsoft YaHei',sans-serif;max-width:760px;\">");
        sb.append("<h1 style=\"font-size:20px;\">").append(esc(report.getTitle())).append("</h1>");
        sb.append("<p style=\"color:#888;font-size:12px;\">报告编号：").append(esc(report.getReportCode()))
                .append("｜范围：").append(esc(report.getScopeText()))
                .append("｜由数据库监控平台定时生成，完整报告（含趋势图）请登录平台「报告中心」查看</p>");
        Object sectionsObj = report.getContent() == null ? null : report.getContent().get("sections");
        if (sectionsObj instanceof List<?> sections) {
            for (Object s : sections) {
                if (s instanceof Map<?, ?> section) {
                    appendSection(sb, (Map<String, Object>) section);
                }
            }
        }
        sb.append("<p style=\"text-align:center;color:#aaa;font-size:12px;margin-top:24px;\">--- 报告结束 ---</p></div>");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendSection(StringBuilder sb, Map<String, Object> section) {
        String type = str(section.get("type"));
        sb.append("<h2 style=\"font-size:15px;border-left:4px solid #0c7c97;padding-left:8px;\">")
                .append(esc(str(section.get("title")))).append("</h2>");
        switch (type) {
            case "summary" -> {
                String summary = str(section.get("summary"));
                if (StringUtils.hasText(summary)) {
                    sb.append("<p style=\"font-size:13px;line-height:1.8;\">").append(esc(summary)).append("</p>");
                }
                if (section.get("kv") instanceof List<?> kv && !kv.isEmpty()) {
                    sb.append(tableOpen());
                    for (Object item : kv) {
                        if (item instanceof Map<?, ?> m) {
                            sb.append("<tr><td style=\"background:#f5f6f8;width:130px;\">").append(esc(str(m.get("label"))))
                                    .append("</td><td>").append(esc(str(m.get("value")))).append("</td></tr>");
                        }
                    }
                    sb.append("</table>");
                }
            }
            case "table" -> {
                List<Map<String, Object>> rows = section.get("rows") instanceof List<?> r
                        ? (List<Map<String, Object>>) r : List.of();
                List<Map<String, Object>> columns = section.get("columns") instanceof List<?> c
                        ? (List<Map<String, Object>>) c : List.of();
                if (rows.isEmpty()) {
                    sb.append("<p style=\"color:#888;font-size:13px;\">").append(esc(str(section.get("emptyText")))).append("</p>");
                } else {
                    sb.append(tableOpen()).append("<tr>");
                    for (Map<String, Object> col : columns) {
                        sb.append("<th style=\"background:#f5f6f8;text-align:left;\">").append(esc(str(col.get("label")))).append("</th>");
                    }
                    sb.append("</tr>");
                    for (Map<String, Object> row : rows) {
                        sb.append("<tr>");
                        for (Map<String, Object> col : columns) {
                            sb.append("<td>").append(esc(str(row.get(str(col.get("key")))))).append("</td>");
                        }
                        sb.append("</tr>");
                    }
                    sb.append("</table>");
                }
            }
            case "list" -> {
                sb.append("<ol style=\"font-size:13px;line-height:2;\">");
                if (section.get("items") instanceof List<?> items) {
                    for (Object item : items) {
                        sb.append("<li>").append(esc(str(item))).append("</li>");
                    }
                }
                sb.append("</ol>");
            }
            case "chart" -> sb.append("<p style=\"color:#888;font-size:13px;\">（趋势图请登录平台报告预览页查看）</p>");
            default -> { }
        }
    }

    private static String tableOpen() {
        return "<table border=\"1\" cellspacing=\"0\" cellpadding=\"5\" "
                + "style=\"border-collapse:collapse;font-size:13px;width:100%;border-color:#ddd;\">";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
