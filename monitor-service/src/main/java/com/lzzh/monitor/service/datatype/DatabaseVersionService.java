package com.lzzh.monitor.service.datatype;

import com.lzzh.monitor.api.request.DatabaseVersionRequest;
import com.lzzh.monitor.api.response.DatabaseVersionVo;

import java.util.List;

/** 数据库版本管理服务（系统设置维护用，§5.8）。 */
public interface DatabaseVersionService {

    /**
     * 查询数据库版本列表（可按 dbType 过滤；留空返回全部）。
     *
     * @param dbType 数据库类型过滤（可空）
     * @return 版本管理视图列表（按 dbType + sortOrder 升序）
     */
    List<DatabaseVersionVo> list(String dbType);

    /**
     * 新增数据库版本。
     *
     * @param req 入参
     * @return 新建记录 ID
     */
    Long create(DatabaseVersionRequest req);

    /**
     * 修改数据库版本。
     *
     * @param req 入参（须含 id）
     */
    void update(DatabaseVersionRequest req);

    /**
     * 删除数据库版本。
     *
     * @param id 主键
     */
    void delete(Long id);
}
