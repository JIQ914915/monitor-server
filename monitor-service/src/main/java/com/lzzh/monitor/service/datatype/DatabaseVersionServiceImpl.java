package com.lzzh.monitor.service.datatype;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.DatabaseVersionRequest;
import com.lzzh.monitor.api.response.DatabaseVersionVo;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/** 数据库版本管理服务实现。 */
@Service
public class DatabaseVersionServiceImpl implements DatabaseVersionService {

    @Resource
    private DatabaseVersionMapper mapper;

    @Override
    public List<DatabaseVersionVo> list(String dbType) {
        LambdaQueryWrapper<DatabaseVersion> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dbType)) {
            qw.eq(DatabaseVersion::getDbType, dbType.trim().toLowerCase());
        }
        return mapper.selectList(qw).stream()
                .sorted(Comparator
                        .comparing((DatabaseVersion v) -> v.getDbType() == null ? "" : v.getDbType())
                        .thenComparing(v -> v.getSortOrder() == null ? 0 : v.getSortOrder()))
                .map(this::toVo)
                .toList();
    }

    @Override
    public Long create(DatabaseVersionRequest req) {
        DatabaseVersion entity = toEntity(req);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(DatabaseVersionRequest req) {
        DatabaseVersion entity = toEntity(req);
        mapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private DatabaseVersionVo toVo(DatabaseVersion v) {
        DatabaseVersionVo vo = new DatabaseVersionVo();
        vo.setId(v.getId());
        vo.setDbType(v.getDbType());
        vo.setVersionCode(v.getVersionCode());
        vo.setVersionName(v.getVersionName());
        vo.setSortOrder(v.getSortOrder());
        vo.setDescription(v.getDescription());
        vo.setCreatedAt(v.getCreatedAt());
        vo.setUpdatedAt(v.getUpdatedAt());
        return vo;
    }

    private DatabaseVersion toEntity(DatabaseVersionRequest req) {
        DatabaseVersion entity = new DatabaseVersion();
        entity.setId(req.getId());
        // dbType 统一小写存储，与 database_version 既有数据保持一致
        entity.setDbType(StringUtils.hasText(req.getDbType()) ? req.getDbType().trim().toLowerCase() : null);
        entity.setVersionCode(req.getVersionCode());
        entity.setVersionName(StringUtils.hasText(req.getVersionName())
                ? req.getVersionName()
                : req.getDbType() + " " + req.getVersionCode());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setDescription(req.getDescription());
        return entity;
    }
}
