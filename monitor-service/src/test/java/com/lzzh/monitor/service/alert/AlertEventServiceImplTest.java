package com.lzzh.monitor.service.alert;

import com.lzzh.monitor.dao.entity.AlertEvent;
import com.lzzh.monitor.dao.entity.AlertEventOperateLog;
import com.lzzh.monitor.dao.mapper.AlertEvaluateWindowMapper;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.AlertEventOperateLogMapper;
import com.lzzh.monitor.dao.mapper.AlertNotifyRecordMapper;
import com.lzzh.monitor.dao.mapper.AlertRuleMapper;
import com.lzzh.monitor.dao.mapper.KnowledgeArticleMapper;
import com.lzzh.monitor.dao.mapper.MetricDefinitionMapper;
import com.lzzh.monitor.dao.mapper.MonitorScenarioMapper;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.service.datascope.DataScope;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertEventServiceImpl} 单元测试。
 *
 * <p>重点覆盖用户明确要求的两件事：
 * <ul>
 *   <li>状态机改为单向流转——用 {@code CONFIRMABLE_STATUSES} 等包内常量直接断言允许的来源状态集合，
 *       确保 {@code handling -> confirmed} 这类回退流转已被移除；</li>
 *   <li>处置备注 + 操作流水——每次实际发生的流转都应写入一条 {@code alert_event_operate_log}，
 *       且备注会被裁剪/去空白后写入。</li>
 * </ul>
 */
class AlertEventServiceImplTest {

    private AlertEventMapper alertEventMapper;
    private AlertNotifyRecordMapper notifyRecordMapper;
    private AlertEventOperateLogMapper operateLogMapper;
    private AlertEvaluateWindowMapper evaluateWindowMapper;
    private SysUserMapper sysUserMapper;
    private AlertRuleMapper alertRuleMapper;
    private DataScopeService dataScopeService;
    private MonitorScenarioMapper scenarioMapper;
    private KnowledgeArticleMapper knowledgeArticleMapper;
    private MetricDefinitionMapper metricDefinitionMapper;
    private AlertDrilldownProfileService drilldownProfileService;
    private AlertEventServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisPlusTestSupport.ensureTableInfo(AlertEvent.class, AlertEventOperateLog.class);
    }

    @BeforeEach
    void setUp() {
        alertEventMapper = mock(AlertEventMapper.class);
        notifyRecordMapper = mock(AlertNotifyRecordMapper.class);
        operateLogMapper = mock(AlertEventOperateLogMapper.class);
        evaluateWindowMapper = mock(AlertEvaluateWindowMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        alertRuleMapper = mock(AlertRuleMapper.class);
        dataScopeService = mock(DataScopeService.class);
        scenarioMapper = mock(MonitorScenarioMapper.class);
        knowledgeArticleMapper = mock(KnowledgeArticleMapper.class);
        metricDefinitionMapper = mock(MetricDefinitionMapper.class);
        drilldownProfileService = mock(AlertDrilldownProfileService.class);
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        service = new AlertEventServiceImpl();
        ReflectionTestUtils.setField(service, "alertEventMapper", alertEventMapper);
        ReflectionTestUtils.setField(service, "notifyRecordMapper", notifyRecordMapper);
        ReflectionTestUtils.setField(service, "operateLogMapper", operateLogMapper);
        ReflectionTestUtils.setField(service, "evaluateWindowMapper", evaluateWindowMapper);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(service, "alertRuleMapper", alertRuleMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "scenarioMapper", scenarioMapper);
        ReflectionTestUtils.setField(service, "knowledgeArticleMapper", knowledgeArticleMapper);
        ReflectionTestUtils.setField(service, "metricDefinitionMapper", metricDefinitionMapper);
        ReflectionTestUtils.setField(service, "drilldownProfileService", drilldownProfileService);
    }

    private AlertEvent event(long id, String status) {
        AlertEvent e = new AlertEvent();
        e.setId(id);
        e.setEventCode("EVT-" + id);
        e.setStatus(status);
        return e;
    }

    @Nested
    class TransitionRulesAreUnidirectional {

        @Test
        void confirmOnlyAllowedFromPending_notFromHandling() {
            // 这是本次改造的核心诉求：之前 handling 也能被"确认"打回，形成来回跳转，现已移除。
            assertThat(AlertEventServiceImpl.CONFIRMABLE_STATUSES).containsExactly("pending");
            assertThat(AlertEventServiceImpl.CONFIRMABLE_STATUSES).doesNotContain("handling");
        }

        @Test
        void handlingAllowedFromPendingOrConfirmed() {
            assertThat(AlertEventServiceImpl.HANDLINGABLE_STATUSES)
                    .containsExactlyInAnyOrder("pending", "confirmed");
        }

        @Test
        void closeAllowedFromConfirmedOrHandling() {
            assertThat(AlertEventServiceImpl.CLOSEABLE_STATUSES)
                    .containsExactlyInAnyOrder("confirmed", "handling");
        }

        @Test
        void silenceAllowedFromAnyActiveStatus() {
            assertThat(AlertEventServiceImpl.ACTIVE_STATUSES)
                    .containsExactlyInAnyOrder("pending", "confirmed", "handling");
        }
    }

    @Nested
    class Confirm {

        @Test
        void transitionsMatchedEventsAndWritesOperateLogWithRemark() {
            AlertEvent matched = event(1L, "pending");
            when(alertEventMapper.selectList(any())).thenReturn(List.of(matched));
            when(alertEventMapper.update(any(), any())).thenReturn(1);

            int updated = service.confirm(List.of(1L), 100L, "张三", "  已知悉，正在排查  ");

            assertThat(updated).isEqualTo(1);
            ArgumentCaptor<AlertEventOperateLog> captor = ArgumentCaptor.forClass(AlertEventOperateLog.class);
            verify(operateLogMapper, times(1)).insert(captor.capture());
            AlertEventOperateLog log = captor.getValue();
            assertThat(log.getEventId()).isEqualTo(1L);
            assertThat(log.getOperateType()).isEqualTo("confirm");
            assertThat(log.getFromStatus()).isEqualTo("pending");
            assertThat(log.getToStatus()).isEqualTo("confirmed");
            assertThat(log.getOperatorId()).isEqualTo(100L);
            assertThat(log.getOperatorName()).isEqualTo("张三");
            // 备注应被 trim
            assertThat(log.getRemark()).isEqualTo("已知悉，正在排查");
        }

        @Test
        void blankRemarkNormalizedToNull() {
            when(alertEventMapper.selectList(any())).thenReturn(List.of(event(1L, "pending")));
            when(alertEventMapper.update(any(), any())).thenReturn(1);

            service.confirm(List.of(1L), 100L, "张三", "   ");

            ArgumentCaptor<AlertEventOperateLog> captor = ArgumentCaptor.forClass(AlertEventOperateLog.class);
            verify(operateLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getRemark()).isNull();
        }

        @Test
        void overlongRemarkTruncatedTo500Chars() {
            when(alertEventMapper.selectList(any())).thenReturn(List.of(event(1L, "pending")));
            when(alertEventMapper.update(any(), any())).thenReturn(1);
            String longRemark = "a".repeat(600);

            service.confirm(List.of(1L), 100L, "张三", longRemark);

            ArgumentCaptor<AlertEventOperateLog> captor = ArgumentCaptor.forClass(AlertEventOperateLog.class);
            verify(operateLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getRemark()).hasSize(500);
        }

        @Test
        void noMatchedEventsShortCircuitsWithoutUpdateOrLog() {
            when(alertEventMapper.selectList(any())).thenReturn(List.of());

            int updated = service.confirm(List.of(999L), 100L, "张三", null);

            assertThat(updated).isZero();
            verify(alertEventMapper, never()).update(any(), any());
            verify(operateLogMapper, never()).insert(any(AlertEventOperateLog.class));
        }
    }

    @Nested
    class Handling {

        @Test
        void writesHandlingOperateLogPerMatchedEvent() {
            AlertEvent e1 = event(1L, "pending");
            AlertEvent e2 = event(2L, "confirmed");
            when(alertEventMapper.selectList(any())).thenReturn(List.of(e1, e2));
            // 逐事件 UPDATE：每次调用影响 1 行
            when(alertEventMapper.update(any(), any())).thenReturn(1);

            int updated = service.handling(List.of(1L, 2L), 100L, "李四", "开始处理");

            assertThat(updated).isEqualTo(2);
            ArgumentCaptor<AlertEventOperateLog> captor = ArgumentCaptor.forClass(AlertEventOperateLog.class);
            verify(operateLogMapper, times(2)).insert(captor.capture());
            List<AlertEventOperateLog> logs = captor.getAllValues();
            assertThat(logs).extracting(AlertEventOperateLog::getFromStatus)
                    .containsExactlyInAnyOrder("pending", "confirmed");
            assertThat(logs).allMatch(l -> "handling".equals(l.getToStatus()));
            assertThat(logs).allMatch(l -> "handling".equals(l.getOperateType()));
        }

        @Test
        void guardFailedEventSkipsOperateLogAndNotCounted() {
            // TOCTOU：查询后第 2 个事件被评估线程改为 recovered，UPDATE 守卫返回 0 行，
            // 该事件不写审计、不计入返回值
            AlertEvent e1 = event(1L, "pending");
            AlertEvent e2 = event(2L, "confirmed");
            when(alertEventMapper.selectList(any())).thenReturn(List.of(e1, e2));
            when(alertEventMapper.update(any(), any())).thenReturn(1).thenReturn(0);

            int updated = service.handling(List.of(1L, 2L), 100L, "李四", "开始处理");

            assertThat(updated).isEqualTo(1);
            ArgumentCaptor<AlertEventOperateLog> captor = ArgumentCaptor.forClass(AlertEventOperateLog.class);
            verify(operateLogMapper, times(1)).insert(captor.capture());
            assertThat(captor.getValue().getEventId()).isEqualTo(1L);
        }
    }

    @Nested
    class Silence {

        @Test
        void rejectsInvalidSilenceHours() {
            assertThatThrownBy(() -> service.silence(List.of(1L), 100L, "张三", 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.silence(List.of(1L), 100L, "张三", null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void transitionsToIgnoredAndLogsSilenceOperation() {
            when(alertEventMapper.selectList(any())).thenReturn(List.of(event(1L, "handling")));
            when(alertEventMapper.update(any(), any())).thenReturn(1);

            int updated = service.silence(List.of(1L), 100L, "王五", 2, "误报，先屏蔽");

            assertThat(updated).isEqualTo(1);
            ArgumentCaptor<AlertEventOperateLog> captor = ArgumentCaptor.forClass(AlertEventOperateLog.class);
            verify(operateLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getOperateType()).isEqualTo("silence");
            assertThat(captor.getValue().getToStatus()).isEqualTo("ignored");
            assertThat(captor.getValue().getFromStatus()).isEqualTo("handling");
        }
    }

    @Nested
    class Close {

        @Test
        void transitionsToClosedAndLogsCloseOperation() {
            when(alertEventMapper.selectList(any())).thenReturn(List.of(event(1L, "confirmed")));
            when(alertEventMapper.update(any(), any())).thenReturn(1);

            int updated = service.close(List.of(1L), 100L, "赵六", "已处理完毕");

            assertThat(updated).isEqualTo(1);
            ArgumentCaptor<AlertEventOperateLog> captor = ArgumentCaptor.forClass(AlertEventOperateLog.class);
            verify(operateLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getOperateType()).isEqualTo("close");
            assertThat(captor.getValue().getToStatus()).isEqualTo("closed");
        }
    }

    @Nested
    class OperateLogQuery {

        @Test
        void returnsEmptyListWhenEventIdNull() {
            assertThat(service.listOperateLogs(null)).isEmpty();
            verify(operateLogMapper, never()).selectList(any());
        }

        @Test
        void mapsEntitiesToVo() {
            AlertEventOperateLog log = new AlertEventOperateLog();
            log.setId(1L);
            log.setEventId(10L);
            log.setOperateType("confirm");
            log.setFromStatus("pending");
            log.setToStatus("confirmed");
            log.setOperatorId(100L);
            log.setOperatorName("张三");
            log.setRemark("备注");
            when(operateLogMapper.selectList(any())).thenReturn(List.of(log));

            var vos = service.listOperateLogs(10L);

            assertThat(vos).hasSize(1);
            assertThat(vos.get(0).getOperateType()).isEqualTo("confirm");
            assertThat(vos.get(0).getRemark()).isEqualTo("备注");
        }
    }
}
