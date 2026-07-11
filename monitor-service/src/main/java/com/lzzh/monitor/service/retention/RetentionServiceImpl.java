package com.lzzh.monitor.service.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.RetentionRequest;
import com.lzzh.monitor.api.response.RetentionVo;
import com.lzzh.monitor.dao.entity.RetentionConfig;
import com.lzzh.monitor.dao.mapper.RetentionConfigMapper;
import com.lzzh.monitor.service.convert.RetentionConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RetentionServiceImpl implements RetentionService {

    /** 出厂默认（天），与 V2__seed_data.sql 保持一致。 */
    private static final Map<String, Integer> FACTORY = new LinkedHashMap<>();

    static {
        FACTORY.put("minute", 7);
        FACTORY.put("hourly", 30);
        FACTORY.put("daily", 365);
        FACTORY.put("slow_sql_sample", 7);
        FACTORY.put("event", 365);
        FACTORY.put("log", 180);
        FACTORY.put("report", 365);
    }

    @Resource
    private RetentionConfigMapper mapper;
    @Resource
    private RetentionPolicyApplier policyApplier;

    /**
     * 出厂默认（天），与 V2 种子一致。
     *
     * @return 类别到默认保留天数的映射
     */
    @Override
    public Map<String, Integer> factory() {
        return new LinkedHashMap<>(FACTORY);
    }

    /**
     * 当前全部类别配置。
     *
     * @return 数据保留策略列表
     */
    @Override
    public List<RetentionVo> list() {
        return mapper.selectList(new LambdaQueryWrapper<RetentionConfig>().orderByAsc(RetentionConfig::getId))
                .stream().map(RetentionConverter::toVo).toList();
    }

    /**
     * 批量保存（按 category upsert）。
     *
     * @param configs 各类别保留策略配置列表
     */
    @Override
    public void save(List<RetentionRequest> configs) {
        if (configs == null) {
            return;
        }
        for (RetentionRequest req : configs) {
            RetentionConfig cfg = RetentionConverter.toEntity(req);
            RetentionConfig existing = mapper.selectOne(
                    new LambdaQueryWrapper<RetentionConfig>().eq(RetentionConfig::getCategory, cfg.getCategory()));
            if (existing == null) {
                if (cfg.getEnabled() == null) {
                    cfg.setEnabled(true);
                }
                mapper.insert(cfg);
                policyApplier.apply(cfg.getCategory(), cfg.getRetentionDays(), Boolean.TRUE.equals(cfg.getEnabled()));
            } else {
                existing.setRetentionDays(cfg.getRetentionDays());
                if (cfg.getEnabled() != null) {
                    existing.setEnabled(cfg.getEnabled());
                }
                mapper.updateById(existing);
                policyApplier.apply(existing.getCategory(), existing.getRetentionDays(),
                        Boolean.TRUE.equals(existing.getEnabled()));
            }
        }
    }

    /**
     * 启动时把 retention_config 现有配置同步为真实 TimescaleDB 保留策略，
     * 保证「重启后 DB 策略 == 配置」（策略下发幂等：remove + add）。
     */
    @Override
    public void syncPoliciesFromConfig() {
        List<RetentionConfig> all = mapper.selectList(new LambdaQueryWrapper<>());
        for (RetentionConfig cfg : all) {
            if (RetentionPolicyApplier.isHypertableCategory(cfg.getCategory())) {
                policyApplier.apply(cfg.getCategory(), cfg.getRetentionDays(), Boolean.TRUE.equals(cfg.getEnabled()));
            }
        }
    }
}
