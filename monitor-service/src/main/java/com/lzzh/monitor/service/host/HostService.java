package com.lzzh.monitor.service.host;

import com.lzzh.monitor.api.request.HostConnectionTestRequest;
import com.lzzh.monitor.api.request.HostPageRequest;
import com.lzzh.monitor.api.request.HostRequest;
import com.lzzh.monitor.api.response.HostCollectTargetVo;
import com.lzzh.monitor.api.response.HostOptionVo;
import com.lzzh.monitor.api.response.HostVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 主机管理（登记、启停、连通性测试）与主机采集分片。 */
public interface HostService {

    PageResult<HostVo> page(HostPageRequest query);

    HostVo getById(Long id);

    Long create(HostRequest request);

    void update(HostRequest request);

    /** 删除主机：仍有实例关联时拒绝删除。 */
    void delete(Long id);

    /** 暂停/恢复采集（仅允许 normal / paused，abnormal 由采集器自动维护）。 */
    void toggleStatus(Long id, String status);

    /** 主机下拉选项（实例表单选择用）。 */
    List<HostOptionVo> listOptions();

    /**
     * 连通性测试：HTTP 探测 exporter /metrics。
     *
     * @return 成功时返回描述文本（node_exporter 版本 + 可解析指标行数）
     */
    String testConnection(HostConnectionTestRequest req);

    /**
     * 主机采集分片：{@code ABS(HASHTEXT(host_code)) % shardTotal = shardIndex}，
     * 携带每台主机关联的实例 ID 列表（扇出写入目标）。
     */
    List<HostCollectTargetVo> listByShard(int shardIndex, int shardTotal);
}
