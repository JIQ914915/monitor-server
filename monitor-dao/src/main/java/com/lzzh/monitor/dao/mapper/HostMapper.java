package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.Host;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HostMapper extends BaseMapper<Host> {

    /**
     * 主机采集分片下推查询：与实例分片同一算法
     * {@code ABS(HASHTEXT(host_code)) % shardTotal = shardIndex}，
     * 以稳定不复用的 host_code 作哈希输入，新增/删除主机不影响既有主机分片归属。
     */
    List<Host> selectByShard(@Param("shardIndex") int shardIndex,
                             @Param("shardTotal") int shardTotal);
}
