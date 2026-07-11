package com.lzzh.monitor.collector.job;

import com.lzzh.monitor.api.response.HostCollectTargetVo;
import com.lzzh.monitor.collector.runner.HostCollectRunner;
import com.lzzh.monitor.service.host.HostService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 主机指标采集任务（分钟级，分片广播）。
 * <p>建议 Cron：{@code 10 * * * * ?}（与 MySQL 分钟采集错峰 10 秒）。
 */
@Component
public class HostCollectJobHandler {

    private final HostService hostService;
    private final HostCollectRunner hostCollectRunner;

    public HostCollectJobHandler(HostService hostService, HostCollectRunner hostCollectRunner) {
        this.hostService = hostService;
        this.hostCollectRunner = hostCollectRunner;
    }

    @XxlJob("hostCollectJobHandler")
    public void hostCollect() {
        int idx = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        List<HostCollectTargetVo> shard = hostService.listByShard(idx, total);
        XxlJobHelper.log("分片 {}/{} 待采集主机数: {}", idx, total, shard.size());
        hostCollectRunner.run(shard);
    }
}
