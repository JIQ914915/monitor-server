package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import org.apache.ibatis.annotations.Mapper;

/** 数据库版本配置 Mapper。 */
@Mapper
public interface DatabaseVersionMapper extends BaseMapper<DatabaseVersion> {
}
