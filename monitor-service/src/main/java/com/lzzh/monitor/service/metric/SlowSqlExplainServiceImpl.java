package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.request.SlowSqlExplainRequest;
import com.lzzh.monitor.api.response.SlowSqlExplainVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.security.PasswordCipher;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadata;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadataService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 慢 SQL 实时执行计划实现。
 *
 * <p>安全边界：
 * <ul>
 *   <li>仅拼接 {@code EXPLAIN } 前缀执行，EXPLAIN 只做优化器分析不执行语句本身；</li>
 *   <li>语句首关键词白名单（SELECT/INSERT/UPDATE/DELETE/REPLACE/WITH/TABLE），
 *       拒绝 DDL、管理命令与 p_s 截断的不完整语句；</li>
 *   <li>去掉尾部分号后禁止语句内再出现分号（连接串未开 allowMultiQueries，双重保险）；</li>
 *   <li>连接超时 5s、查询超时 10s，用后即断，不建池（按需低频操作）。</li>
 * </ul>
 */
@Service
public class SlowSqlExplainServiceImpl implements SlowSqlExplainService {

    private static final Set<String> ALLOWED_FIRST_KEYWORDS =
            Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "REPLACE", "WITH", "TABLE");

    private static final int QUERY_TIMEOUT_SECONDS = 10;

    @Resource
    private DbInstanceMapper instanceMapper;
    @Resource
    private InstanceRuntimeMetadataService runtimeMetadataService;
    @Resource
    private PasswordCipher passwordCipher;

    @Override
    public SlowSqlExplainVo explain(SlowSqlExplainRequest request) {
        String sql = sanitizeSql(request.getSql());

        DbInstance ins = instanceMapper.selectById(request.getInstanceId());
        if (ins == null) {
            throw new BusinessException("实例不存在: " + request.getInstanceId());
        }
        InstanceRuntimeMetadata metadata = runtimeMetadataService.getRequired(request.getInstanceId());
        if (!StringUtils.hasText(metadata.urlTemplate())) {
            throw new BusinessException("实例对应数据库类型缺少 JDBC URL 模板，请在【数据库类型管理】中补全");
        }

        String schema = StringUtils.hasText(request.getSchemaName())
                ? request.getSchemaName() : ins.getDatabaseName();
        String url = metadata.urlTemplate()
                .replace("{host}", ins.getHost() == null ? "" : ins.getHost())
                .replace("{port}", ins.getPort() == null ? "" : String.valueOf(ins.getPort()))
                .replace("{database}", schema == null ? "" : schema);

        DriverManager.setLoginTimeout(5);
        try (Connection conn = DriverManager.getConnection(url,
                ins.getConnUser(), passwordCipher.decrypt(ins.getConnPassword()));
             Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("EXPLAIN " + sql)) {
                return readResult(rs);
            }
        } catch (SQLException e) {
            throw new BusinessException("执行计划获取失败：" + rootMessage(e));
        }
    }

    /** 校验并规整待 EXPLAIN 的 SQL：去尾部分号、拒绝多语句、拒绝截断语句、限定语句类型。 */
    private static String sanitizeSql(String raw) {
        String sql = raw == null ? "" : raw.trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.isEmpty()) {
            throw new BusinessException("SQL 为空，无法生成执行计划");
        }
        if (sql.contains(";")) {
            throw new BusinessException("仅支持对单条语句生成执行计划");
        }
        if (sql.endsWith("...")) {
            throw new BusinessException("该 SQL 采集时已被目标库截断（受 performance_schema_max_sql_text_length 限制），"
                    + "语句不完整无法生成执行计划，请复制补全后在目标库手动 EXPLAIN");
        }
        String firstWord = sql.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        // 归一化 "(SELECT" 这类带括号开头
        firstWord = firstWord.replaceFirst("^\\(+", "");
        if (!ALLOWED_FIRST_KEYWORDS.contains(firstWord)) {
            throw new BusinessException("仅支持对 SELECT / INSERT / UPDATE / DELETE / REPLACE 语句生成执行计划");
        }
        return sql;
    }

    private static SlowSqlExplainVo readResult(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                Object v = rs.getObject(i);
                row.add(v == null ? null : String.valueOf(v));
            }
            rows.add(row);
        }
        SlowSqlExplainVo vo = new SlowSqlExplainVo();
        vo.setColumns(columns);
        vo.setRows(rows);
        return vo;
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
