package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsMetricTrendMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TsMetricTrendDao} 小时级取数路由测试：
 * 天生小时指标直查 1h 原始表；分钟级指标走 cagg 降采样 + 1m 现算补头/补尾；
 * 无 TimescaleDB 环境整段回退 1m 现算。
 */
class TsMetricTrendDaoTest {

    private static final long HOUR = 3600_000L;
    private static final Long INSTANCE_ID = 1L;
    private static final String CODE = "mysql.qps";

    private TsMetricTrendMapper mapper;
    private TsMetricTrendDao dao;

    /** 对齐到整点的基准时间，避免桶边界受当前时刻影响。 */
    private final long base = System.currentTimeMillis() / HOUR * HOUR - 24 * HOUR;

    @BeforeEach
    void setUp() {
        mapper = mock(TsMetricTrendMapper.class);
        dao = new TsMetricTrendDao(mapper);
    }

    private static Map<String, Object> row(long ts, double value) {
        Map<String, Object> m = new HashMap<>();
        m.put("collect_time", new Timestamp(ts));
        m.put("value", value);
        return m;
    }

    @Nested
    class HourlyRouting {

        @Test
        void nativeHourlyMetricReadsRaw1hTable() {
            when(mapper.selectTrend1h(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(row(base, 100), row(base + HOUR, 110)));

            List<TsMetricTrendDao.TrendPoint> points =
                    dao.queryTrendByFrequency(INSTANCE_ID, "mysql.capacity.total_size_bytes", base, base + 2 * HOUR, "1h");

            assertThat(points).hasSize(2);
            // 原始 1h 表有数据时不再查 cagg / 1m
            verify(mapper, never()).selectTrend1hCagg(anyLong(), anyString(), any(), any());
            verify(mapper, never()).selectTrend1hAggFrom1m(anyLong(), anyString(), any(), any());
        }

        @Test
        void minuteMetricUsesCaggWithTailFillFrom1m() {
            when(mapper.selectTrend1h(anyLong(), anyString(), any(), any())).thenReturn(List.of());
            when(mapper.caggExists()).thenReturn(true);
            // cagg 覆盖到 base+2h（刷新滞后），查询范围到 base+4h
            when(mapper.selectTrend1hCagg(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(row(base, 10), row(base + HOUR, 11), row(base + 2 * HOUR, 12)));
            when(mapper.selectTrend1hAggFrom1m(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(row(base + 3 * HOUR, 13), row(base + 4 * HOUR, 14)));

            List<TsMetricTrendDao.TrendPoint> points =
                    dao.queryTrendByFrequency(INSTANCE_ID, CODE, base, base + 4 * HOUR, "1h");

            assertThat(points).hasSize(5);
            assertThat(points.get(0).value()).isEqualTo(10);
            assertThat(points.get(4).value()).isEqualTo(14);
            // 补尾查询的起点应为 cagg 最后一个桶 + 1h
            verify(mapper).selectTrend1hAggFrom1m(INSTANCE_ID, CODE,
                    new Timestamp(base + 3 * HOUR), new Timestamp(base + 4 * HOUR));
        }

        @Test
        void caggFullyCoveringRangeSkipsTailFill() {
            when(mapper.selectTrend1h(anyLong(), anyString(), any(), any())).thenReturn(List.of());
            when(mapper.caggExists()).thenReturn(true);
            when(mapper.selectTrend1hCagg(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(row(base, 10), row(base + HOUR, 11)));

            List<TsMetricTrendDao.TrendPoint> points =
                    dao.queryTrendByFrequency(INSTANCE_ID, CODE, base, base + HOUR, "1h");

            assertThat(points).hasSize(2);
            verify(mapper, never()).selectTrend1hAggFrom1m(anyLong(), anyString(), any(), any());
        }

        @Test
        void emptyCaggFallsBackToOnTheFlyAggregation() {
            when(mapper.selectTrend1h(anyLong(), anyString(), any(), any())).thenReturn(List.of());
            when(mapper.caggExists()).thenReturn(true);
            when(mapper.selectTrend1hCagg(anyLong(), anyString(), any(), any())).thenReturn(List.of());
            when(mapper.selectTrend1hAggFrom1m(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(row(base, 10)));

            List<TsMetricTrendDao.TrendPoint> points =
                    dao.queryTrendByFrequency(INSTANCE_ID, CODE, base, base + HOUR, "1h");

            assertThat(points).hasSize(1);
            verify(mapper).selectTrend1hAggFrom1m(INSTANCE_ID, CODE, new Timestamp(base), new Timestamp(base + HOUR));
        }

        @Test
        void noTimescaleEnvironmentAggregatesWholeRangeFrom1m() {
            when(mapper.selectTrend1h(anyLong(), anyString(), any(), any())).thenReturn(List.of());
            when(mapper.caggExists()).thenReturn(false);
            when(mapper.selectTrend1hAggFrom1m(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(row(base, 10)));

            List<TsMetricTrendDao.TrendPoint> points =
                    dao.queryTrendByFrequency(INSTANCE_ID, CODE, base, base + HOUR, "1h");

            assertThat(points).hasSize(1);
            verify(mapper, never()).selectTrend1hCagg(anyLong(), anyString(), any(), any());
        }

        @Test
        void headFillWhenRangeStartsBeforeCaggCoverage() {
            when(mapper.selectTrend1h(anyLong(), anyString(), any(), any())).thenReturn(List.of());
            when(mapper.caggExists()).thenReturn(true);
            // cagg 覆盖从 base+2h 开始（部署前历史未物化），查询从 base 开始
            when(mapper.selectTrend1hCagg(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(row(base + 2 * HOUR, 12), row(base + 3 * HOUR, 13)));
            when(mapper.selectTrend1hAggFrom1m(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(row(base, 10), row(base + HOUR, 11)));

            List<TsMetricTrendDao.TrendPoint> points =
                    dao.queryTrendByFrequency(INSTANCE_ID, CODE, base, base + 3 * HOUR, "1h");

            assertThat(points).hasSize(4);
            assertThat(points.get(0).ts()).isEqualTo(base);
            assertThat(points.get(3).ts()).isEqualTo(base + 3 * HOUR);
            // 补头查询上界应为 cagg 首桶前 1ms
            verify(mapper).selectTrend1hAggFrom1m(INSTANCE_ID, CODE,
                    new Timestamp(base), new Timestamp(base + 2 * HOUR - 1));
        }
    }

    @Test
    void minuteFrequencyReadsRaw1mTable() {
        when(mapper.selectTrend1m(anyLong(), anyString(), any(), any()))
                .thenReturn(List.of(row(base, 1), row(base + 60_000, 2)));

        List<TsMetricTrendDao.TrendPoint> points =
                dao.queryTrendByFrequency(INSTANCE_ID, CODE, base, base + HOUR, "1m");

        assertThat(points).hasSize(2);
        verify(mapper, never()).selectTrend1h(anyLong(), anyString(), any(), any());
    }
}
