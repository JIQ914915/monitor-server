package com.lzzh.monitor.service.datatype;

import com.lzzh.monitor.api.request.DatabaseTypeRequest;
import com.lzzh.monitor.api.response.DatabaseTypeVo;
import com.lzzh.monitor.api.response.DbTypeOptionVo;
import com.lzzh.monitor.api.response.DbVersionOptionVo;

import java.util.List;

/**
 * 数据库类型/版本/采集器映射服务（§5.8 扩展性核心）。
 * 维护 database_type / database_version 表，向前端提供类型/版本下拉选项，向采集端提供
 * 「类型 → 采集器实现」的查询；新增数据库类型时只需登记记录 + 新增 monitor-collector-&lt;db&gt; 模块。
 */
public interface DatabaseTypeService {

    /**
     * 全部数据库类型（管理视图，含禁用），供系统设置页维护。
     *
     * @return 数据库类型管理视图列表（按 sortOrder 升序）
     */
    List<DatabaseTypeVo> listAll();

    /**
     * 新增数据库类型。
     *
     * @param req 入参
     * @return 新建记录 ID
     */
    Long create(DatabaseTypeRequest req);

    /**
     * 修改数据库类型。
     *
     * @param req 入参（须含 id）
     */
    void update(DatabaseTypeRequest req);

    /**
     * 删除数据库类型。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 启用的数据库类型选项（含默认端口与版本列表），供新增/编辑实例下拉动态获取。
     *
     * @return 数据库类型选项列表（按 sortOrder 升序）
     */
    List<DbTypeOptionVo> listTypeOptions();

    /**
     * 某数据库类型的版本选项（大小写不敏感匹配 code/label）。
     *
     * @param dbType 数据库类型（code 或 label 均可，如 MYSQL / MySQL）
     * @return 版本选项列表（按 sortOrder 升序）
     */
    List<DbVersionOptionVo> listVersionOptions(String dbType);

    /**
     * 校验某类型+版本是否在支持矩阵内。
     *
     * @param dbType  数据库类型
     * @param version 数据库版本
     * @return 支持返回 true，否则 false
     */
    boolean isSupported(String dbType, String version);

    /**
     * 取某类型登记的采集器实现类全限定名。
     *
     * @param dbType 数据库类型
     * @return 采集器实现类全限定名
     */
    String getCollectorClass(String dbType);
}
