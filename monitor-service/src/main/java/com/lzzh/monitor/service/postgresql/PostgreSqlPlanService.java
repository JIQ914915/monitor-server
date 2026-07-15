package com.lzzh.monitor.service.postgresql;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lzzh.monitor.api.request.PgPlanCaptureRequest;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.PgPlanHistoryVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.mapper.PgDiagnosticMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PostgreSqlPlanService {
    private static final Set<String> ALLOWED = Set.of("SELECT", "WITH", "TABLE");
    @Resource private PgDiagnosticMapper mapper;

    PgPlanHistoryVo capture(CollectTargetVo target, PgPlanCaptureRequest request) {
        String sql = sanitizePlanSql(request.getSql());
        String planJson;
        try (Connection conn = open(target, request.getDatabase()); Statement st = conn.createStatement()) {
            st.setQueryTimeout(15);
            try (ResultSet rs = st.executeQuery("EXPLAIN (FORMAT JSON, COSTS TRUE, VERBOSE FALSE) " + sql)) {
                if (!rs.next()) throw new BusinessException("目标库未返回执行计划");
                planJson = rs.getString(1);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("安全 EXPLAIN 失败：" + rootMessage(e));
        }

        try {
            JSONArray plan = JSONUtil.parseArray(planJson);
            List<Map<String, Object>> summary = new ArrayList<>();
            summarize(plan.getJSONObject(0).getJSONObject("Plan"), 0, "0", summary);
            String canonical = plan.toString();
            String planHash = sha256(canonical);
            Map<String, Object> latest = mapper.selectLatestPlan(
                    request.getInstanceId(), request.getDatabase(), request.getQueryId());
            String previous = latest == null ? null : text(latest, "plan_hash");
            Map<String, Object> record = new HashMap<>();
            record.put("instanceId", request.getInstanceId());
            record.put("database", request.getDatabase());
            record.put("queryId", request.getQueryId());
            record.put("sqlHash", sha256(sql.replaceAll("\\s+", " ").trim()));
            record.put("planHash", planHash);
            record.put("queryText", sql);
            record.put("planJson", canonical);
            record.put("summaryJson", JSONUtil.toJsonStr(summary));
            record.put("previousPlanHash", previous);
            record.put("planChanged", previous != null && !previous.equals(planHash));
            mapper.insertPlan(record);
            return history(request.getInstanceId(), request.getDatabase(), request.getQueryId())
                    .stream().findFirst().orElseThrow(() -> new BusinessException("执行计划保存失败"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("执行计划解析失败：" + rootMessage(e));
        }
    }

    List<PgPlanHistoryVo> history(Long instanceId, String database, String queryId) {
        if (!StringUtils.hasText(database) || !StringUtils.hasText(queryId)) {
            throw new BusinessException("数据库和 Query ID 不能为空");
        }
        return mapper.selectPlanHistory(instanceId, database, queryId, 50)
                .stream().map(this::toVo).toList();
    }

    static String sanitizePlanSql(String raw) {
        String sql = raw == null ? "" : raw.trim();
        while (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();
        if (!StringUtils.hasText(sql)) throw new BusinessException("SQL 不能为空");
        if (sql.contains(";")) throw new BusinessException("只允许单条 SQL");
        if (sql.matches("(?s).*\\$\\d+.*")) {
            throw new BusinessException("SQL 含 $1 等参数占位符，请替换为脱敏后的示例常量");
        }
        if (sql.contains("/*") || sql.contains("--")) {
            throw new BusinessException("为避免语句边界歧义，不支持带注释 SQL");
        }
        String first = sql.split("\\s+", 2)[0].replaceFirst("^\\(+", "").toUpperCase(Locale.ROOT);
        if (!ALLOWED.contains(first)) {
            throw new BusinessException("安全计划采集仅支持 SELECT / WITH / TABLE，且不会执行 ANALYZE");
        }
        return sql;
    }

    private void summarize(JSONObject node, int depth, String path, List<Map<String, Object>> out) {
        if (node == null) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", path); item.put("depth", depth);
        copy(node, item, "Node Type", "nodeType");
        copy(node, item, "Relation Name", "relation");
        copy(node, item, "Index Name", "index");
        copy(node, item, "Join Type", "joinType");
        copy(node, item, "Startup Cost", "startupCost");
        copy(node, item, "Total Cost", "totalCost");
        copy(node, item, "Plan Rows", "planRows");
        copy(node, item, "Filter", "filter");
        copy(node, item, "Index Cond", "indexCondition");
        copy(node, item, "Hash Cond", "hashCondition");
        copy(node, item, "Merge Cond", "mergeCondition");
        out.add(item);
        JSONArray children = node.getJSONArray("Plans");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                summarize(children.getJSONObject(i), depth + 1, path + "." + i, out);
            }
        }
    }

    private static void copy(JSONObject node, Map<String, Object> target, String source, String key) {
        if (node.containsKey(source)) target.put(key, node.get(source));
    }

    @SuppressWarnings("unchecked")
    private PgPlanHistoryVo toVo(Map<String, Object> row) {
        PgPlanHistoryVo vo = new PgPlanHistoryVo();
        vo.setId(longNumber(row, "id")); vo.setDatabase(text(row, "database_name"));
        vo.setQueryId(text(row, "query_id")); vo.setSqlHash(text(row, "sql_hash"));
        vo.setPlanHash(text(row, "plan_hash")); vo.setPreviousPlanHash(text(row, "previous_plan_hash"));
        vo.setPlanChanged(Boolean.TRUE.equals(row.get("plan_changed")));
        try {
            vo.setPlan(JSONUtil.parse(String.valueOf(row.get("plan_json"))));
            vo.setNodeSummary((List<Map<String, Object>>) (List<?>)
                    JSONUtil.toList(JSONUtil.parseArray(String.valueOf(row.get("node_summary"))), Map.class));
        } catch (Exception e) {
            vo.setNodeSummary(List.of());
        }
        Object captured = row.get("captured_at");
        if (captured instanceof OffsetDateTime time) vo.setCapturedAt(time);
        else if (captured instanceof Timestamp time) {
            vo.setCapturedAt(time.toLocalDateTime().atOffset(OffsetDateTime.now().getOffset()));
        }
        return vo;
    }

    private static Connection open(CollectTargetVo target, String database) throws Exception {
        DriverManager.setLoginTimeout(5);
        String url = target.getUrlTemplate().replace("{host}", target.getHost())
                .replace("{port}", String.valueOf(target.getPort())).replace("{database}", database);
        return DriverManager.getConnection(url, target.getConnUser(), target.getConnPassword());
    }
    private static String sha256(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(bytes);
    }
    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key); return value == null ? null : String.valueOf(value);
    }
    private static long longNumber(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number n ? n.longValue() : value == null ? 0 : Long.parseLong(value.toString());
    }
    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}