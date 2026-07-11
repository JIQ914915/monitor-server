package com.lzzh.monitor.collector.host.prom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prometheus 文本暴露格式（text/plain; version=0.0.4）的轻量解析器。
 *
 * <p>只解析白名单内的 metric family（node_exporter 全量输出上千行，逐行判断前缀后
 * 白名单外的行直接跳过），不引入 prometheus 客户端依赖。支持的行形态：
 * <pre>
 *   node_load1 1.23
 *   node_cpu_seconds_total{cpu="0",mode="idle"} 4.5e+06
 * </pre>
 * 标签值支持 {@code \\ \" \n} 转义；# 开头的 HELP/TYPE 注释行跳过；
 * 值为 NaN/+Inf/-Inf 的采样丢弃（对本产品指标无意义）。
 */
public final class PromTextParser {

    private PromTextParser() {
    }

    /**
     * 解析指标文本。
     *
     * @param body     exporter /metrics 响应体
     * @param families 需要解析的 metric family 白名单
     */
    public static PromSnapshot parse(String body, Set<String> families) {
        Map<String, List<PromSample>> result = new HashMap<>();
        if (body != null) {
            body.lines().forEach(line -> parseLine(line, families, result));
        }
        return new PromSnapshot(result, System.currentTimeMillis());
    }

    private static void parseLine(String line, Set<String> families, Map<String, List<PromSample>> out) {
        if (line.isEmpty() || line.charAt(0) == '#') {
            return;
        }
        // family 名 = 行首到 '{' 或空格
        int braceIdx = line.indexOf('{');
        int spaceIdx = line.indexOf(' ');
        int nameEnd = braceIdx >= 0 && (spaceIdx < 0 || braceIdx < spaceIdx) ? braceIdx : spaceIdx;
        if (nameEnd <= 0) {
            return;
        }
        String family = line.substring(0, nameEnd);
        if (!families.contains(family)) {
            return;
        }
        try {
            Map<String, String> labels;
            String valuePart;
            if (nameEnd == braceIdx) {
                int close = findClosingBrace(line, braceIdx + 1);
                if (close < 0) {
                    return;
                }
                labels = parseLabels(line.substring(braceIdx + 1, close));
                valuePart = line.substring(close + 1).trim();
            } else {
                labels = Map.of();
                valuePart = line.substring(nameEnd + 1).trim();
            }
            // 值后可能带时间戳（"value timestamp"），只取第一段
            int vs = valuePart.indexOf(' ');
            if (vs > 0) {
                valuePart = valuePart.substring(0, vs);
            }
            double value = Double.parseDouble(valuePart);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return;
            }
            out.computeIfAbsent(family, k -> new ArrayList<>()).add(new PromSample(labels, value));
        } catch (RuntimeException ignored) {
            // 单行格式异常不影响其余行
        }
    }

    /** 找标签段的闭合 '}'（跳过带引号的标签值内部字符）。 */
    private static int findClosingBrace(String line, int from) {
        boolean inQuote = false;
        for (int i = from; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inQuote = false;
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == '}') {
                return i;
            }
        }
        return -1;
    }

    /** 解析 {@code k1="v1",k2="v2"} 形式的标签段。 */
    private static Map<String, String> parseLabels(String segment) {
        Map<String, String> labels = new HashMap<>();
        int i = 0;
        int len = segment.length();
        while (i < len) {
            int eq = segment.indexOf('=', i);
            if (eq < 0) {
                break;
            }
            String key = segment.substring(i, eq).trim();
            int vStart = segment.indexOf('"', eq);
            if (vStart < 0) {
                break;
            }
            StringBuilder value = new StringBuilder();
            int j = vStart + 1;
            while (j < len) {
                char c = segment.charAt(j);
                if (c == '\\' && j + 1 < len) {
                    char next = segment.charAt(j + 1);
                    value.append(switch (next) {
                        case 'n' -> '\n';
                        case '\\' -> '\\';
                        case '"' -> '"';
                        default -> next;
                    });
                    j += 2;
                } else if (c == '"') {
                    break;
                } else {
                    value.append(c);
                    j++;
                }
            }
            labels.put(key, value.toString());
            // 跳过闭合引号与后续逗号
            i = j + 1;
            while (i < len && (segment.charAt(i) == ',' || segment.charAt(i) == ' ')) {
                i++;
            }
        }
        return labels;
    }
}
