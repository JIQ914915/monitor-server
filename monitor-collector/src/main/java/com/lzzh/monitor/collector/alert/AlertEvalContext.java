package com.lzzh.monitor.collector.alert;

import com.lzzh.monitor.dao.entity.AlertEvaluateWindow;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertRuleInstanceConfig;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 单轮告警评估的状态快照（本轮内随建单/恢复同步维护）。
 * <p>评估轮开始时批量预载：内置规则实例级配置、分片内活跃事件、持续窗口、静默窗口，
 * 消除规则×实例逐对查库。xxl-job 分片广播保证同一实例本轮只由当前节点评估，快照在本轮内可安全读写。
 */
public record AlertEvalContext(Map<String, AlertRuleInstanceConfig> builtinConfigs,
                               Map<String, AlertEvent> activeEvents,
                               Map<String, AlertEvaluateWindow> windows,
                               Map<String, OffsetDateTime> silenceUntil) {

    public static String builtinConfigKey(String ruleCode, Long instanceId) {
        return (ruleCode == null ? "" : ruleCode) + "|" + instanceId;
    }

    public static String windowKey(String dedupKey, String windowType) {
        return (dedupKey == null ? "" : dedupKey) + "|" + (windowType == null ? "" : windowType);
    }

    public AlertRuleInstanceConfig builtinConfig(String ruleCode, Long instanceId) {
        return builtinConfigs.get(builtinConfigKey(ruleCode, instanceId));
    }

    public AlertEvent activeEvent(String dedupKey) {
        return activeEvents.get(dedupKey);
    }

    public AlertEvaluateWindow window(String dedupKey, String windowType) {
        return windows.get(windowKey(dedupKey, windowType));
    }

    public boolean hasWindow(String dedupKey, String windowType) {
        return windows.containsKey(windowKey(dedupKey, windowType));
    }

    public void putWindow(AlertEvaluateWindow window) {
        windows.put(windowKey(window.getDedupKey(), window.getWindowType()), window);
    }

    public void removeWindow(String dedupKey, String windowType) {
        windows.remove(windowKey(dedupKey, windowType));
    }
}
