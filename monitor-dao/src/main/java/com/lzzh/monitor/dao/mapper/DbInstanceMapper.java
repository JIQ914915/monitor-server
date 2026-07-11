package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.DbInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DbInstanceMapper extends BaseMapper<DbInstance> {

    /**
     * 分片下推查询（P1-4）：用 PostgreSQL {@code HASHTEXT} 在 SQL 层过滤当前分片的实例，
     * 避免全量载入内存后再 Java 侧 CRC32 取模（1000 实例时内存/扫描热点，§3.3-G）。
     *
     * <p>全系统分片算法统一为 {@code ABS(HASHTEXT(instance_code)) % shardTotal = shardIndex}：
     * 以稳定且不复用的 instance_code（而非自增 id）作哈希输入，删除/新增实例不影响既有实例分片归属。
     * shardTotal=1 时恒等式 {@code % 1 = 0} 保证全量返回，兼容本地单机调度场景。
     *
     * @param shardIndex 当前分片序号（0-based）
     * @param shardTotal 分片总数
     * @return 属于该分片的实例列表（含全部列）
     */
    @Select("SELECT * FROM db_instance "
            + "WHERE ABS(HASHTEXT(instance_code)) % #{shardTotal} = #{shardIndex}")
    List<DbInstance> selectByShard(@Param("shardIndex") int shardIndex,
                                   @Param("shardTotal") int shardTotal);

    /**
     * 查询指定用户直接负责（负责人A或负责人B）的实例 ID 集合，数据范围校验组件依据之一。
     *
     * @param userId 用户ID
     * @return 该用户负责的实例 ID 列表
     */
    @Select("SELECT id FROM db_instance WHERE owner_a_id = #{userId} OR owner_b_id = #{userId}")
    List<Long> selectOwnedInstanceIds(@Param("userId") Long userId);

    /**
     * 查询归属于给定分组集合（任一分组）的实例 ID 集合，数据范围校验组件依据之一。
     *
     * @param groupIds 分组ID集合，禁止为空（空集合调用方应自行短路，避免生成非法 SQL）
     * @return 归属其中任一分组的实例 ID 列表
     */
    @Select("<script>"
            + "SELECT DISTINCT id FROM db_instance WHERE "
            + "<foreach collection='groupIds' item='gid' open='(' separator=' OR ' close=')'>"
            + "group_ids @> CONCAT('[', #{gid}, ']')::jsonb"
            + "</foreach>"
            + "</script>")
    List<Long> selectIdsByGroupIds(@Param("groupIds") Collection<Long> groupIds);
}
