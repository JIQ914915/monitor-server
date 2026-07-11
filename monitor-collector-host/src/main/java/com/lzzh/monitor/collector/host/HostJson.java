package com.lzzh.monitor.collector.host;

import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;

/**
 * 主机明细文本指标的 JSON 序列化（hutool）。
 * 统一保留 null 字段：明细行中 inodeUsagePercent 等字段的 null 有「该口径不适用」的语义，
 * 与历史数据格式保持一致，前端按字段判空渲染。
 */
public final class HostJson {

    private static final JSONConfig CONFIG = JSONConfig.create().setIgnoreNullValue(false);

    private HostJson() {
    }

    public static String toJson(Object obj) {
        return JSONUtil.toJsonStr(obj, CONFIG);
    }
}
