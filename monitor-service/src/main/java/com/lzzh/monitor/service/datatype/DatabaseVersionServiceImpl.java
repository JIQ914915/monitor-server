package com.lzzh.monitor.service.datatype;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.DatabaseVersionRequest;
import com.lzzh.monitor.api.response.DatabaseVersionVo;
import com.lzzh.monitor.common.datatype.DatabaseTypeCode;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 数据库版本管理服务实现。 */
@Service
public class DatabaseVersionServiceImpl implements DatabaseVersionService {

    @Resource
    private DatabaseVersionMapper mapper;
    @Resource
    private DatabaseTypeMapper databaseTypeMapper;

    @Override
    public List<DatabaseVersionVo> list(Long dbTypeId) {
        List<DatabaseType> types = databaseTypeMapper.selectList(null);
        Map<Long, DatabaseType> typeMap = types.stream().collect(Collectors.toMap(
                DatabaseType::getId, type -> type, (left, right) -> left));
        LambdaQueryWrapper<DatabaseVersion> query = new LambdaQueryWrapper<>();
        if (dbTypeId != null) {
            requireType(dbTypeId);
            query.eq(DatabaseVersion::getDbTypeId, dbTypeId);
        }
        return mapper.selectList(query).stream()
                .sorted(Comparator
                        .comparing((DatabaseVersion version) -> version.getDbTypeId() == null ? 0L : version.getDbTypeId())
                        .thenComparing(version -> version.getSortOrder() == null ? 0 : version.getSortOrder()))
                .map(version -> toVo(version, typeMap.get(version.getDbTypeId())))
                .toList();
    }

    @Override
    public Long create(DatabaseVersionRequest request) {
        DatabaseVersion entity = toEntity(request);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(DatabaseVersionRequest request) {
        mapper.updateById(toEntity(request));
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private DatabaseType requireType(Long id) {
        DatabaseType type = id == null ? null : databaseTypeMapper.selectById(id);
        if (type == null || !StringUtils.hasText(type.getCode())) {
            throw new BusinessException("数据库类型不存在: " + id);
        }
        return type;
    }

    private DatabaseVersionVo toVo(DatabaseVersion version, DatabaseType type) {
        DatabaseVersionVo vo = new DatabaseVersionVo();
        vo.setId(version.getId());
        vo.setDbTypeId(version.getDbTypeId());
        vo.setDbType(type == null ? null : DatabaseTypeCode.normalize(type.getCode()));
        vo.setVersionCode(version.getVersionCode());
        vo.setVersionName(version.getVersionName());
        vo.setSortOrder(version.getSortOrder());
        vo.setDescription(version.getDescription());
        vo.setCreatedAt(version.getCreatedAt());
        vo.setUpdatedAt(version.getUpdatedAt());
        return vo;
    }

    private DatabaseVersion toEntity(DatabaseVersionRequest request) {
        DatabaseType type = requireType(request.getDbTypeId());
        DatabaseVersion entity = new DatabaseVersion();
        entity.setId(request.getId());
        entity.setDbTypeId(type.getId());
        entity.setVersionCode(request.getVersionCode());
        entity.setVersionName(StringUtils.hasText(request.getVersionName())
                ? request.getVersionName() : type.getLabel() + " " + request.getVersionCode());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setDescription(request.getDescription());
        return entity;
    }
}
