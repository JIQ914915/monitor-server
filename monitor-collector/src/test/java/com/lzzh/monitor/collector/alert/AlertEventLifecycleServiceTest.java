package com.lzzh.monitor.collector.alert;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lzzh.monitor.collector.config.AlertNotifyProperties;
import com.lzzh.monitor.dao.entity.AlertEvaluateWindow;
import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertRule;
import com.lzzh.monitor.dao.entity.AlertRuleInstanceConfig;
import com.lzzh.monitor.dao.mapper.AlertEvaluateWindowMapper;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleInstanceConfigMapper;
import com.lzzh.monitor.dao.mapper.ScenarioInstanceConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertEventLifecycleService} 针对性单元测试。
 * <p>聚焦评审中反复关注的三类时序问题：
 * <ul>
 *   <li>status 守卫失败（评估期间事件被人工终态处置）时的轮内缓存剔除与返回值；</li>
 *   <li>通知字段回写行数与"已通知"统计（dispatched / TRIGGERED_AND_NOTIFIED）对齐；</li>
 *   <li>持续窗口 touch 后以 DB 回查的 first_match_time 判定，而非轮内缓存。</li>
 * </ul>
 */
class AlertEventLifecycleServiceTest {

    private static final Long INSTANCE_ID = 1L;
    private static final String RULE_CODE = "cpu_high";
    private static final String DEDUP_KEY = RULE_CODE + ":" + INSTANCE_ID;

    private AlertEventMapper alertEventMapper;
    private AlertEvaluateWindowMapper windowMapper;
    private AlertRuleInstanceConfigMapper instanceConfigMapper;
    private ScenarioInstanceConfigMapper scenarioConfigMapper;
    private AlertNotificationService notificationService;
    private AlertMessageRenderer messageRenderer;
    private AlertEventLifecycleService service;

    /**
     * 纯 Mockito 单测（不启动 Spring）里构造 LambdaUpdateWrapper 需要实体的 TableInfo 元数据，
     * 平时由 Spring Boot 扫描 Mapper 惰性建立，这里手动初始化（同 monitor-service 的 MybatisPlusTestSupport）。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entityClass : new Class<?>[]{AlertEvent.class, AlertEvaluateWindow.class, AlertRuleInstanceConfig.class}) {
            if (TableInfoHelper.getTableInfo(entityClass) == null) {
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace(entityClass.getName());
                TableInfoHelper.initTableInfo(assistant, entityClass);
            }
        }
    }

    @BeforeEach
    void setUp() {
        alertEventMapper = mock(AlertEventMapper.class);
        windowMapper = mock(AlertEvaluateWindowMapper.class);
        instanceConfigMapper = mock(AlertRuleInstanceConfigMapper.class);
        scenarioConfigMapper = mock(ScenarioInstanceConfigMapper.class);
        notificationService = mock(AlertNotificationService.class);
        messageRenderer = mock(AlertMessageRenderer.class);
        when(messageRenderer.render(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("msg");
        service = new AlertEventLifecycleService(alertEventMapper, windowMapper, instanceConfigMapper,
                scenarioConfigMapper, notificationService, messageRenderer, new AlertNotifyProperties());
    }

    private static AlertRule rule(int durationSec) {
        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setRuleCode(RULE_CODE);
        rule.setRuleName("CPU 使用率过高");
        rule.setRuleLevel("warning");
        rule.setMetricName("mysql.cpu.usage");
        Map<String, Object> cond = new HashMap<>();
        cond.put("operator", ">");
        cond.put("threshold", 90);
        if (durationSec > 0) {
            cond.put("duration", durationSec);
        }
        rule.setConditionConfig(cond);
        return rule;
    }

    private static AlertEvalContext emptyCtx() {
        return new AlertEvalContext(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private static AlertEvent activeEvent() {
        AlertEvent e = new AlertEvent();
        e.setId(100L);
        e.setEventCode("AE_TEST");
        e.setDedupKey(DEDUP_KEY);
        e.setStatus("pending");
        e.setTriggerCount(1);
        e.setEvalState("normal");
        e.setTriggerTime(OffsetDateTime.now().minusMinutes(10));
        return e;
    }

    @Nested
    class UpsertGuard {

        @Test
        void guardFailedUpdateEvictsCacheAndSkips() {
            AlertEvalContext ctx = emptyCtx();
            AlertEvent existing = activeEvent();
            ctx.activeEvents().put(DEDUP_KEY, existing);
            // 主更新守卫失败：事件已在评估期间被人工终态处置
            when(alertEventMapper.update(isNull(), any())).thenReturn(0);

            AlertUpsertResult result = service.handleTriggerWithWindow(ctx, rule(0), INSTANCE_ID, "ins", null, 95);

            assertThat(result).isEqualTo(AlertUpsertResult.DISPOSED_SKIPPED);
            assertThat(ctx.activeEvents()).doesNotContainKey(DEDUP_KEY);
            verify(notificationService, never()).notifyOnTrigger(any(), any());
        }

        @Test
        void notifyFieldWriteFailedCountsAsTriggeredOnly() {
            AlertEvalContext ctx = emptyCtx();
            AlertEvent existing = activeEvent();
            ctx.activeEvents().put(DEDUP_KEY, existing);
            // 第一次 update（主更新）成功，第二次（通知字段回写）守卫失败
            when(alertEventMapper.update(isNull(), any())).thenReturn(1, 0);
            when(notificationService.notifyOnTrigger(any(), any())).thenReturn(true);

            AlertUpsertResult result = service.handleTriggerWithWindow(ctx, rule(0), INSTANCE_ID, "ins", null, 95);

            // 通知已派发但字段回写未生效：不计入"已通知"，内存快照通知字段不更新
            assertThat(result).isEqualTo(AlertUpsertResult.TRIGGERED_ONLY);
            assertThat(existing.getNotifyCount()).isNull();
            assertThat(existing.getLastNotifyTime()).isNull();
            // 触发字段的轮内快照仍然同步
            assertThat(existing.getTriggerCount()).isEqualTo(2);
        }

        @Test
        void notifyFieldWriteSucceededCountsAsNotified() {
            AlertEvalContext ctx = emptyCtx();
            AlertEvent existing = activeEvent();
            ctx.activeEvents().put(DEDUP_KEY, existing);
            when(alertEventMapper.update(isNull(), any())).thenReturn(1, 1);
            when(notificationService.notifyOnTrigger(any(), any())).thenReturn(true);

            AlertUpsertResult result = service.handleTriggerWithWindow(ctx, rule(0), INSTANCE_ID, "ins", null, 95);

            assertThat(result).isEqualTo(AlertUpsertResult.TRIGGERED_AND_NOTIFIED);
            assertThat(existing.getNotifyCount()).isEqualTo(1);
            assertThat(existing.getLastNotifyTime()).isNotNull();
        }
    }

    @Nested
    class RecoverGuard {

        @Test
        void guardFailedRecoverReturnsFalseAndEvictsCache() {
            AlertEvalContext ctx = emptyCtx();
            ctx.activeEvents().put(DEDUP_KEY, activeEvent());
            when(alertEventMapper.update(isNull(), any())).thenReturn(0);

            boolean recovered = service.handleRecoverWithWindow(ctx, rule(0), INSTANCE_ID, "ins", null, 50);

            assertThat(recovered).isFalse();
            assertThat(ctx.activeEvents()).doesNotContainKey(DEDUP_KEY);
            verify(notificationService, never()).notifyOnRecovery(any(), any());
        }

        @Test
        void successfulRecoverReturnsTrueAndEvictsCache() {
            AlertEvalContext ctx = emptyCtx();
            ctx.activeEvents().put(DEDUP_KEY, activeEvent());
            when(alertEventMapper.update(isNull(), any())).thenReturn(1);
            when(notificationService.notifyOnRecovery(any(), any())).thenReturn(false);

            boolean recovered = service.handleRecoverWithWindow(ctx, rule(0), INSTANCE_ID, "ins", null, 50);

            assertThat(recovered).isTrue();
            assertThat(ctx.activeEvents()).doesNotContainKey(DEDUP_KEY);
        }
    }

    @Nested
    class MetricMissing {

        @Test
        void ruleDisabledEvictsCacheWithoutDbWrite() {
            AlertEvalContext ctx = emptyCtx();
            ctx.activeEvents().put(DEDUP_KEY, activeEvent());
            // 回查规则启用状态：已停用
            when(instanceConfigMapper.selectCount(any())).thenReturn(0L);

            boolean created = service.markMetricMissing(ctx, rule(0), INSTANCE_ID, "ins", null, false);

            assertThat(created).isFalse();
            assertThat(ctx.activeEvents()).doesNotContainKey(DEDUP_KEY);
            verify(alertEventMapper, never()).update(isNull(), any());
        }

        @Test
        void alreadyMissingSkipsEnabledCheckAndDbWrite() {
            AlertEvalContext ctx = emptyCtx();
            AlertEvent existing = activeEvent();
            existing.setEvalState("metric_missing");
            ctx.activeEvents().put(DEDUP_KEY, existing);

            boolean created = service.markMetricMissing(ctx, rule(0), INSTANCE_ID, "ins", null, false);

            assertThat(created).isFalse();
            // 已处于 metric_missing 的后续轮次不产生任何点查/写库
            verify(instanceConfigMapper, never()).selectCount(any());
            verify(alertEventMapper, never()).update(isNull(), any());
        }
    }

    @Nested
    class TriggerWindow {

        @Test
        void freshWindowStaysPending() {
            AlertEvalContext ctx = emptyCtx();
            // 无活跃事件 + duration=60s：走持续窗口；DB 回查到的 first_match_time 是刚刚
            when(windowMapper.selectWindow(anyString(), anyString()))
                    .thenReturn(window(OffsetDateTime.now()));

            AlertUpsertResult result = service.handleTriggerWithWindow(ctx, rule(60), INSTANCE_ID, "ins", null, 95);

            assertThat(result).isEqualTo(AlertUpsertResult.WINDOW_PENDING);
            verify(alertEventMapper, never()).insert(any(AlertEvent.class));
        }

        @Test
        void elapsedWindowCreatesEvent() {
            AlertEvalContext ctx = emptyCtx();
            // DB 回查到的 first_match_time 已超过 duration，应放行建单
            when(windowMapper.selectWindow(anyString(), anyString()))
                    .thenReturn(window(OffsetDateTime.now().minusSeconds(120)));
            when(instanceConfigMapper.selectCount(any())).thenReturn(1L);
            when(alertEventMapper.insert(any(AlertEvent.class))).thenReturn(1);
            when(notificationService.notifyOnTrigger(any(), any())).thenReturn(false);

            AlertUpsertResult result = service.handleTriggerWithWindow(ctx, rule(60), INSTANCE_ID, "ins", null, 95);

            assertThat(result).isEqualTo(AlertUpsertResult.TRIGGERED_ONLY);
            verify(alertEventMapper).insert(any(AlertEvent.class));
            assertThat(ctx.activeEvents()).containsKey(DEDUP_KEY);
        }

        @Test
        void newEventSkippedWhenRuleDisabledDuringEvaluation() {
            AlertEvalContext ctx = emptyCtx();
            when(windowMapper.selectWindow(anyString(), anyString()))
                    .thenReturn(window(OffsetDateTime.now().minusSeconds(120)));
            // 评估期间规则被停用：建单前回查应拦截
            when(instanceConfigMapper.selectCount(any())).thenReturn(0L);

            AlertUpsertResult result = service.handleTriggerWithWindow(ctx, rule(60), INSTANCE_ID, "ins", null, 95);

            assertThat(result).isEqualTo(AlertUpsertResult.DISPOSED_SKIPPED);
            verify(alertEventMapper, never()).insert(any(AlertEvent.class));
        }

        private AlertEvaluateWindow window(OffsetDateTime firstMatchTime) {
            AlertEvaluateWindow w = new AlertEvaluateWindow();
            w.setDedupKey(DEDUP_KEY);
            w.setWindowType("trigger");
            w.setFirstMatchTime(firstMatchTime);
            return w;
        }
    }

}
