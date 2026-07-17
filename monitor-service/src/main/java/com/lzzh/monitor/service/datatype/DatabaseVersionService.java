package com.lzzh.monitor.service.datatype;

import com.lzzh.monitor.api.request.DatabaseVersionRequest;
import com.lzzh.monitor.api.response.DatabaseVersionVo;

import java.util.List;

/** 数据库版本管理服务。 */
public interface DatabaseVersionService {
    List<DatabaseVersionVo> list(Long dbTypeId);
    Long create(DatabaseVersionRequest request);
    void update(DatabaseVersionRequest request);
    void delete(Long id);
}
