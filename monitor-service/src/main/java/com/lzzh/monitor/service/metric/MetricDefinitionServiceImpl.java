package com.lzzh.monitor.service.metric;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.dao.mapper.MetricDefinitionMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标定义服务实现（P1-2）。
 * <p>启动时全量加载 metric_definition 到内存双缓存：
 * <ul>
 *   <li>{@code codeMap}：metricCode → MetricDefinition，O(1) 精确查找</li>
 *   <li>{@code typeIndex}：dbType（小写）→ List，按 dbType 批量取</li>
 * </ul>
 * 修改指标定义后调用 {@link #refreshCache()} 热更新缓存，无需重启。
 */
@Service
public class MetricDefinitionServiceImpl implements MetricDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(MetricDefinitionServiceImpl.class);

    @Resource
    private MetricDefinitionMapper mapper;

    /** metricCode → MetricDefinition */
    private volatile Map<String, MetricDefinition> codeMap = new ConcurrentHashMap<>();
    /** dbType（小写）→ List<MetricDefinition> */
    private volatile Map<String, List<MetricDefinition>> typeIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    @Override
    public void refreshCache() {
        try {
            List<MetricDefinition> all = mapper.selectList(
                    new LambdaQueryWrapper<MetricDefinition>()
                            .eq(MetricDefinition::getEnabled, true)
                            .orderByAsc(MetricDefinition::getMetricCode));
            Map<String, MetricDefinition> newCodeMap = new ConcurrentHashMap<>(all.size() * 2);
            Map<String, List<MetricDefinition>> newTypeIndex = new ConcurrentHashMap<>();
            for (MetricDefinition def : all) {
                if (def.getMetricCode() != null) {
                    newCodeMap.put(def.getMetricCode(), def);
                }
                if (def.getDbType() != null) {
                    newTypeIndex.computeIfAbsent(def.getDbType().toLowerCase(), k -> new ArrayList<>()).add(def);
                }
            }
            this.codeMap = newCodeMap;
            this.typeIndex = newTypeIndex;
            log.info("MetricDefinition 缓存已刷新：共 {} 条", all.size());
        } catch (Exception e) {
            log.error("MetricDefinition 缓存刷新失败", e);
        }
    }

    @Override
    public List<MetricDefinition> listByDbType(String dbType) {
        if (dbType == null) {
            return List.of();
        }
        return typeIndex.getOrDefault(dbType.toLowerCase(), List.of());
    }

    @Override
    public Optional<MetricDefinition> findByCode(String metricCode) {
        if (metricCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(codeMap.get(metricCode));
    }

    @Override
    public List<MetricDefinition> listAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<MetricDefinition>().orderByAsc(MetricDefinition::getMetricCode));
    }
}
