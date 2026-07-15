package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.request.InstanceRequest;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.InstanceVo;
import com.lzzh.monitor.dao.entity.DbInstance;

/**
 * 实例实体 ↔ DTO 转换（实体不越出 service 层）。
 *
 * <p>toVo / toCollectTarget 均接收已由 service 解析好的展示字段（dbType、dbVersion 等），
 * 不从实体上读取临时字段，保持实体职责单一（只描述表结构）。
 */
public final class InstanceConverter {

    private InstanceConverter() {
    }

    /**
     * 实体 → 展示 VO。
     *
     * @param e          实体（持久字段）
     * @param dbType     数据库类型展示名，由 service 从 database_type 解析
     * @param dbVersion  数据库版本编码，由 service 从 database_version 解析
     */
    public static InstanceVo toVo(DbInstance e, String dbType, String dbVersion) {
        if (e == null) {
            return null;
        }
        InstanceVo v = new InstanceVo();
        v.setId(e.getId());
        v.setName(e.getName());
        v.setHost(e.getHost());
        v.setPort(e.getPort());
        v.setDbTypeId(e.getDbTypeId());
        v.setDbVersionId(e.getDbVersionId());
        v.setDbType(dbType);
        v.setDbVersion(dbVersion);
        v.setDatabaseName(e.getDatabaseName());
        v.setPgObjectScope(e.getPgObjectScope());
        v.setPgObjectDatabases(e.getPgObjectDatabases());
        v.setHostId(e.getHostId());
        v.setRemark(e.getRemark());
        v.setGroupIds(e.getGroupIds());
        v.setConnSourceWhitelist(e.getConnSourceWhitelist());
        v.setOwnerAId(e.getOwnerAId());
        v.setOwnerBId(e.getOwnerBId());
        v.setConnUser(e.getConnUser());
        v.setHealth(e.getHealth());
        v.setStatus(e.getStatus());
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /** 请求 → 实体。 */
    public static DbInstance toEntity(InstanceRequest r) {
        if (r == null) {
            return null;
        }
        DbInstance e = new DbInstance();
        e.setId(r.getId());
        e.setName(r.getName());
        e.setHost(r.getHost());
        e.setPort(r.getPort());
        e.setDbTypeId(r.getDbTypeId());
        e.setDbVersionId(r.getDbVersionId());
        e.setDatabaseName(r.getDatabaseName());
        e.setPgObjectScope(r.getPgObjectScope());
        e.setPgObjectDatabases(r.getPgObjectDatabases());
        e.setHostId(r.getHostId());
        e.setRemark(r.getRemark());
        e.setGroupIds(r.getGroupIds());
        e.setConnSourceWhitelist(r.getConnSourceWhitelist());
        e.setOwnerAId(r.getOwnerAId());
        e.setOwnerBId(r.getOwnerBId());
        e.setConnUser(r.getConnUser());
        e.setConnPassword(r.getConnPassword());
        e.setHealth(r.getHealth());
        e.setStatus(r.getStatus());
        return e;
    }

    /**
     * 实体 → 采集目标 VO（含连接凭据，仅供 collector 内部使用）。
     *
     * @param e           实体（持久字段）
     * @param dbType      数据库类型展示名
     * @param dbVersion   数据库版本编码
     * @param driverClass JDBC 驱动类名，来自 database_type
     * @param urlTemplate JDBC URL 模板，来自 database_type
     */
    public static CollectTargetVo toCollectTarget(DbInstance e,
                                                   String dbType, String dbVersion,
                                                   String driverClass, String urlTemplate) {
        if (e == null) {
            return null;
        }
        CollectTargetVo t = new CollectTargetVo();
        t.setId(e.getId());
        t.setInstanceCode(e.getInstanceCode());
        t.setInstanceName(e.getName());
        t.setDbType(dbType);
        t.setDbVersion(dbVersion);
        t.setDriverClass(driverClass);
        t.setUrlTemplate(urlTemplate);
        t.setHost(e.getHost());
        t.setPort(e.getPort());
        t.setConnUser(e.getConnUser());
        t.setConnPassword(e.getConnPassword());
        t.setStatus(e.getStatus());
        t.setDatabaseName(e.getDatabaseName());
        t.setPgObjectScope(e.getPgObjectScope());
        t.setPgObjectDatabases(e.getPgObjectDatabases());
        t.setConnSourceWhitelist(e.getConnSourceWhitelist());
        return t;
    }
}
