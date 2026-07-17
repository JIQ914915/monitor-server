package com.lzzh.monitor.service.metric;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.common.datatype.DatabaseTypeCode;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
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

/** 指标定义缓存：类型索引统一使用 database_type.code。 */
@Service
public class MetricDefinitionServiceImpl implements MetricDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(MetricDefinitionServiceImpl.class);

    @Resource
    private MetricDefinitionMapper mapper;
    @Resource
    private DatabaseTypeMapper databaseTypeMapper;

    private volatile Map<String, MetricDefinition> codeMap = new ConcurrentHashMap<>();
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
            for (MetricDefinition definition : all) {
                if (definition.getMetricCode() != null) {
                    newCodeMap.put(definition.getMetricCode(), definition);
                }
                String typeCode = DatabaseTypeCode.normalize(definition.getDbType());
                if (typeCode != null) {
                    newTypeIndex.computeIfAbsent(typeCode, ignored -> new ArrayList<>()).add(definition);
                }
            }
            codeMap = newCodeMap;
            typeIndex = newTypeIndex;
            log.info("MetricDefinition 缓存已刷新：共 {} 条", all.size());
        } catch (Exception e) {
            log.error("MetricDefinition 缓存刷新失败", e);
        }
    }

    @Override
    public List<MetricDefinition> listByDbTypeId(Long dbTypeId) {
        if (dbTypeId == null) {
            return List.of();
        }
        DatabaseType type = databaseTypeMapper.selectById(dbTypeId);
        if (type == null) {
            throw new BusinessException("数据库类型不存在: " + dbTypeId);
        }
        return typeIndex.getOrDefault(DatabaseTypeCode.normalize(type.getCode()), List.of());
    }

    @Override
    public Optional<MetricDefinition> findByCode(String metricCode) {
        return metricCode == null ? Optional.empty() : Optional.ofNullable(codeMap.get(metricCode));
    }

    @Override
    public List<MetricDefinition> listAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<MetricDefinition>().orderByAsc(MetricDefinition::getMetricCode));
    }
}
