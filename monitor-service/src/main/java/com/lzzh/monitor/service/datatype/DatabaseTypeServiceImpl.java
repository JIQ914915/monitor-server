package com.lzzh.monitor.service.datatype;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.DatabaseTypeRequest;
import com.lzzh.monitor.api.response.DatabaseTypeVo;
import com.lzzh.monitor.api.response.DbTypeOptionVo;
import com.lzzh.monitor.api.response.DbVersionOptionVo;
import com.lzzh.monitor.common.datatype.DatabaseTypeCode;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 数据库类型/版本服务实现：表间使用主键关联，code 仅作为采集器稳定标识。 */
@Service
public class DatabaseTypeServiceImpl implements DatabaseTypeService {

    @Resource
    private DatabaseTypeMapper typeMapper;
    @Resource
    private DatabaseVersionMapper versionMapper;

    @Override
    public List<DatabaseTypeVo> listAll() {
        return typeMapper.selectList(new LambdaQueryWrapper<DatabaseType>().orderByAsc(DatabaseType::getSortOrder))
                .stream().map(this::toVo).toList();
    }

    @Override
    public Long create(DatabaseTypeRequest req) {
        DatabaseType entity = toEntity(req);
        typeMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(DatabaseTypeRequest req) {
        DatabaseType entity = toEntity(req);
        typeMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        typeMapper.deleteById(id);
    }

    private DatabaseTypeVo toVo(DatabaseType t) {
        DatabaseTypeVo vo = new DatabaseTypeVo();
        vo.setId(t.getId());
        vo.setCode(t.getCode());
        vo.setLabel(t.getLabel());
        vo.setDriverClass(t.getDriverClass());
        vo.setUrlTemplate(t.getUrlTemplate());
        vo.setCollectorClass(t.getCollectorClass());
        vo.setDefaultPort(t.getDefaultPort());
        vo.setDbIcon(t.getDbIcon());
        vo.setSortOrder(t.getSortOrder());
        vo.setDescription(t.getDescription());
        vo.setEnabled(t.getEnabled());
        return vo;
    }

    private DatabaseType toEntity(DatabaseTypeRequest req) {
        DatabaseType entity = new DatabaseType();
        entity.setId(req.getId());
        entity.setCode(DatabaseTypeCode.normalize(req.getCode()));
        entity.setLabel(req.getLabel());
        entity.setDriverClass(req.getDriverClass());
        entity.setUrlTemplate(req.getUrlTemplate());
        entity.setCollectorClass(req.getCollectorClass());
        entity.setDefaultPort(req.getDefaultPort());
        entity.setDbIcon(req.getDbIcon());
        entity.setSortOrder(req.getSortOrder());
        entity.setDescription(req.getDescription());
        entity.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        return entity;
    }

    @Override
    public List<DbTypeOptionVo> listTypeOptions() {
        List<DatabaseType> types = typeMapper.selectList(new LambdaQueryWrapper<DatabaseType>()
                .eq(DatabaseType::getEnabled, true)
                .orderByAsc(DatabaseType::getSortOrder));
        return types.stream().map(t -> {
            DbTypeOptionVo vo = new DbTypeOptionVo();
            vo.setId(t.getId());
            vo.setCode(t.getCode());
            vo.setLabel(t.getLabel());
            vo.setDefaultPort(t.getDefaultPort());
            vo.setVersions(listVersionOptions(t.getId()));
            return vo;
        }).toList();
    }

    @Override
    public List<DbVersionOptionVo> listVersionOptions(String dbType) {
        if (!StringUtils.hasText(dbType)) {
            return List.of();
        }
        DatabaseType type = typeMapper.selectList(null).stream()
                .filter(t -> DatabaseTypeCode.equals(t.getCode(), dbType)
                        || (t.getLabel() != null && t.getLabel().equalsIgnoreCase(dbType)))
                .findFirst()
                .orElse(null);
        return type == null ? List.of() : listVersionOptions(type.getId());
    }

    private List<DbVersionOptionVo> listVersionOptions(Long dbTypeId) {
        return versionMapper.selectList(new LambdaQueryWrapper<DatabaseVersion>()
                        .eq(DatabaseVersion::getDbTypeId, dbTypeId)
                        .orderByAsc(DatabaseVersion::getSortOrder))
                .stream()
                .map(v -> new DbVersionOptionVo(
                        v.getId(),
                        v.getVersionCode(),
                        StringUtils.hasText(v.getVersionName()) ? v.getVersionName() : v.getVersionCode()))
                .toList();
    }

    @Override
    public boolean isSupported(String dbType, String version) {
        if (!StringUtils.hasText(dbType) || !StringUtils.hasText(version)) {
            return false;
        }
        return listVersionOptions(dbType).stream()
                .anyMatch(v -> version.equalsIgnoreCase(v.getValue()));
    }

    @Override
    public String getCollectorClass(String dbType) {
        if (!StringUtils.hasText(dbType)) {
            return null;
        }
        return typeMapper.selectList(new LambdaQueryWrapper<>())
                .stream()
                .filter(t -> (t.getCode() != null && t.getCode().equalsIgnoreCase(dbType))
                        || (t.getLabel() != null && t.getLabel().equalsIgnoreCase(dbType)))
                .map(DatabaseType::getCollectorClass)
                .findFirst()
                .orElse(null);
    }
}
