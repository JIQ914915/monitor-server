package com.lzzh.monitor.service.alert;

import com.lzzh.monitor.api.request.AlertRulePageRequest;
import com.lzzh.monitor.api.request.AlertRuleSaveRequest;
import com.lzzh.monitor.api.request.BuiltinRulePageRequest;
import com.lzzh.monitor.api.request.BuiltinRuleSaveRequest;
import com.lzzh.monitor.api.response.AlertRuleVo;
import com.lzzh.monitor.common.result.PageResult;

/** 告警规则管理服务。 */
public interface AlertRuleService {

    /** 分页查询告警规则列表。 */
    PageResult<AlertRuleVo> page(AlertRulePageRequest req);

    /** 查询单条规则详情（按 ruleCode）。 */
    AlertRuleVo getByRuleCode(String ruleCode);

    /** 新建或更新规则（id 为空则新建）。 */
    AlertRuleVo save(AlertRuleSaveRequest req);

    /**
     * 切换启用/停用状态。
     * <ul>
     *   <li>内置规则 + instanceId 不为空：写 alert_rule_instance_config 覆盖（不修改全局 enabled）。</li>
     *   <li>自定义规则 / instanceId 为空：直接修改 alert_rule.enabled。</li>
     * </ul>
     */
    void toggleEnabled(String ruleCode, boolean enabled, Long instanceId);

    /**
     * 一键开启系统推荐的常用内置规则（recommended=true，按实例类型/版本/主机关联过滤适配集）。
     * 已启用的规则跳过，不覆盖用户已有配置。
     *
     * @return 本次新开启的规则数
     */
    int enableRecommended(Long instanceId);

    /** 删除规则（按 ruleCode，内置规则不允许删除）。 */
    void delete(String ruleCode);

    /** 更新规则扫描间隔（分钟）。 */
    void updateScanInterval(String ruleCode, int scanIntervalMin, Long instanceId);

    // ── 内置规则模板全局维护（系统设置 → 内置规则管理） ──────────────────────

    /** 分页查询内置规则模板（全局视角，支持按类型/级别/数据来源过滤）。 */
    PageResult<AlertRuleVo> pageBuiltinTemplates(BuiltinRulePageRequest req);

    /** 新建或更新内置规则模板（id 为空则新建；支持产品库指标与目标库 SQL 两种数据来源）。 */
    AlertRuleVo saveBuiltinTemplate(BuiltinRuleSaveRequest req);

    /** 删除内置规则模板：级联关闭各实例活跃事件、清理实例配置与指标关联后删除模板。 */
    void deleteBuiltinTemplate(String ruleCode);
}
