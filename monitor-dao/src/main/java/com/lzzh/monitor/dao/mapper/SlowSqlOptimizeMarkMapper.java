package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.SlowSqlOptimizeMark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SlowSqlOptimizeMarkMapper extends BaseMapper<SlowSqlOptimizeMark> {

    /** 按 (实例, 库, 指纹) upsert 优化状态（schema_name 可空，走 COALESCE 唯一索引）。 */
    int upsertStatus(@Param("instanceId") Long instanceId,
                     @Param("schemaName") String schemaName,
                     @Param("digest") String digest,
                     @Param("status") String status,
                     @Param("updatedBy") String updatedBy);
}
