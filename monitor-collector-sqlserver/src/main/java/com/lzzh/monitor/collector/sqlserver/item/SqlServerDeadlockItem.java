package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.SqlServerDiagnosticEventPoint;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 复用默认 system_health 的 xml_deadlock_report；不创建或修改 XEvent 会话。 */
@Component
public class SqlServerDeadlockItem implements SqlServerMetricItem {
    private static final Pattern INPUT_BUFFER=Pattern.compile("(?s)(<inputbuf[^>]*>)(.*?)(</inputbuf>)");
    @Override public String code() { return "deadlock_events"; }

    @Override
    public void collect(Connection conn, CollectRequest request, SqlServerVersionAdapter adapter,
                        SqlServerMetricSink sink) throws Exception {
        try (Statement st=conn.createStatement()) {
            st.setQueryTimeout(10); st.setMaxRows(20);
            try (ResultSet rs=st.executeQuery(adapter.deadlockEventsSql())) {
                while (rs.next()) {
                    String payload=redactAndLimit(rs.getString("event_xml"));
                    Timestamp eventTime=rs.getTimestamp("event_time");
                    long timestamp=eventTime==null?System.currentTimeMillis():eventTime.getTime();
                    sink.addDiagnosticEvent(new SqlServerDiagnosticEventPoint("deadlock",null,
                            "warning",sha256(payload),payload,true,timestamp));
                }
            }
        }
    }

    static String redactAndLimit(String xml) {
        if (xml==null) return "";
        Matcher matcher=INPUT_BUFFER.matcher(xml); StringBuffer out=new StringBuffer();
        while(matcher.find()) matcher.appendReplacement(out,Matcher.quoteReplacement(
                matcher.group(1)+SqlServerSqlRedactor.redact(matcher.group(2))+matcher.group(3)));
        matcher.appendTail(out);
        return out.length()<=65535?out.toString():out.substring(0,65535);
    }

    private static String sha256(String value) throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder(64);
        for(byte b:bytes) out.append(String.format("%02x",b));
        return out.toString();
    }
}
