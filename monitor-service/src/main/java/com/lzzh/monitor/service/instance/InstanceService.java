package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.api.request.ConnectionTestRequest;
import com.lzzh.monitor.api.request.InstancePageRequest;
import com.lzzh.monitor.api.request.InstanceRequest;
import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.api.response.ConnectionTestVo;
import com.lzzh.monitor.api.response.FleetOverviewVo;
import com.lzzh.monitor.api.response.FleetSummaryVo;
import com.lzzh.monitor.api.response.InstanceVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 实例管理服务。 */
public interface InstanceService {

    /**
     * 分页查询实例。
     *
     * @param query 分页与过滤条件
     * @return 实例分页结果
     */
    PageResult<InstanceVo> page(InstancePageRequest query);

    /**
     * 查询全部实例（实例选择面板用，密码脱敏）。
     *
     * @return 全部实例列表
     */
    List<InstanceVo> listAll();

    /**
     * 按主键查询实例。
     *
     * @param id 实例主键 ID
     * @return 实例详情
     */
    InstanceVo getById(Long id);

    /**
     * 新增实例。
     *
     * @param instance 实例信息
     * @return 新建实例的主键 ID
     */
    Long create(InstanceRequest instance);

    /**
     * 修改实例。
     *
     * @param instance 实例信息（须含主键 ID）
     */
    void update(InstanceRequest instance);

    /**
     * 删除实例。
     *
     * @param id 实例主键 ID
     */
    void delete(Long id);

    /**
     * 暂停/恢复采集：status 仅允许 normal（恢复采集）/ paused（暂停采集）。
     * 注意：abnormal 状态由采集器自动维护，不允许通过此接口写入。
     *
     * @param id     实例主键 ID
     * @param status 目标状态：normal / paused
     */
    void toggleStatus(Long id, String status);

    /**
     * 测试实例连接，成功返回数据库版本号，失败抛出业务异常。
     *
     * @param request 连接测试入参
     * @return 数据库版本号 + 采集账号权限逐项检测结果
     */
    ConnectionTestVo testConnection(ConnectionTestRequest request);

    /**
     * 查询实例舰队概况（仪表盘用）。
     *
     * @return 各状态计数 + 平均健康度 + 健康等级分布
     */
    FleetSummaryVo summary();

    /**
     * 首页全局总览（§11.1.2）：整体健康门面（聚合健康分 + 五维达标率）、5 张状态统计卡、
     * 数据库类型分布、高风险实例 Top10。数据范围为当前用户数据权限内的实例集合。
     *
     * @return 首页全局总览
     */
    FleetOverviewVo fleetOverview();

    /**
     * 按分片取应由当前 collector 节点处理的采集目标（§14.1.3，含连接凭据）。
     * 各频率任务共用同一批实例，频率差异由采集项自身声明（MySqlMetricItem.frequencies）。
     *
     * @param shardIndex 分片索引
     * @param shardTotal 分片总数
     * @return 该分片负责的采集目标列表
     */
    List<CollectTargetVo> listByShard(int shardIndex, int shardTotal);

    /**
     * 按实例 ID 取单个采集目标（含明文连接凭据，仅供服务端内部即席连接使用，
     * 如阻塞链现场快照抓取），实例不存在时返回 null。
     *
     * @param instanceId 实例 ID
     * @return 采集目标（含解密后的连接凭据）
     */
    CollectTargetVo getCollectTarget(Long instanceId);
}
