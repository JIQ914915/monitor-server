package com.lzzh.monitor.collector.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.response.HealthScoreVo;
import com.lzzh.monitor.common.enums.InstanceStatus;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.service.metric.HealthScoreService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康评分任务（P0-3）：定期为所有非暂停实例计算健康分并写回 {@code db_instance.health}，
 * 供实例列表 / 实例选择器等"批量展示"场景直接读取。
 *
 * <p><b>评分口径与实时接口完全一致</b>：委托 {@link HealthScoreService}（五维加权 + 扣分明细，
 * 实时概况页 {@code /metrics/health-score} 用的同一实现），避免两套算法导致列表分数与
 * 实时概况页分数不一致。本任务只负责"算好存起来"，不重复实现评分规则。
 *
 * <p>score = -1（所有维度均无新鲜数据，如实例刚接入、采集长期停止）时跳过写回，
 * 保留上一次的评分，避免把有历史分数的实例清成无效值。
 */
@Component
public class HealthCalculateJobHandler {

    private static final Logger log = LoggerFactory.getLogger(HealthCalculateJobHandler.class);

    private final DbInstanceMapper dbInstanceMapper;
    private final HealthScoreService healthScoreService;

    public HealthCalculateJobHandler(DbInstanceMapper dbInstanceMapper, HealthScoreService healthScoreService) {
        this.dbInstanceMapper = dbInstanceMapper;
        this.healthScoreService = healthScoreService;
    }

    @XxlJob("healthCalculateJobHandler")
    public void calculate() {
        List<DbInstance> instances = dbInstanceMapper.selectList(
                new LambdaQueryWrapper<DbInstance>().ne(DbInstance::getStatus, InstanceStatus.PAUSED));
        if (instances.isEmpty()) {
            XxlJobHelper.log("无实例需要评分");
            return;
        }
        XxlJobHelper.log("开始计算 {} 个实例的健康评分", instances.size());
        int updated = 0, skipped = 0;
        for (DbInstance ins : instances) {
            try {
                HealthScoreVo vo = healthScoreService.calculate(ins.getId());
                if (vo.getScore() < 0) {
                    // 无任何新鲜指标数据：保留旧分，不写回
                    skipped++;
                    continue;
                }
                // updateById 走实体更新，healthDims(jsonb) 才会应用 JacksonTypeHandler 序列化
                DbInstance update = new DbInstance();
                update.setId(ins.getId());
                update.setHealth(vo.getScore());
                update.setHealthDims(toDimMap(vo));
                dbInstanceMapper.updateById(update);
                updated++;
            } catch (Exception e) {
                log.error("实例 {} 健康评分失败", ins.getId(), e);
            }
        }
        XxlJobHelper.log("健康评分完成，更新 {} 个实例，无数据跳过 {} 个", updated, skipped);
    }

    /** 五维得分快照：{"availability":98,...}，供首页整体健康总览聚合达标率（-1=该维度无数据）。 */
    private static Map<String, Integer> toDimMap(HealthScoreVo vo) {
        if (vo.getDimensions() == null || vo.getDimensions().isEmpty()) {
            return null;
        }
        Map<String, Integer> dims = new LinkedHashMap<>();
        for (HealthScoreVo.DimensionScore d : vo.getDimensions()) {
            dims.put(d.getDimension(), d.getScore());
        }
        return dims;
    }
}
