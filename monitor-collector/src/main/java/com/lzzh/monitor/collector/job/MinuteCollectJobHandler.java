package com.lzzh.monitor.collector.job;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.collector.runner.CollectRunner;
import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.service.instance.InstanceService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.util.List;

/** 分钟级采集任务（分片广播：每个 collector 节点只处理本分片实例，§14.1.3）。 */
@Component
public class MinuteCollectJobHandler {

    private final InstanceService instanceService;
    private final CollectRunner collectRunner;

    public MinuteCollectJobHandler(InstanceService instanceService, CollectRunner collectRunner) {
        this.instanceService = instanceService;
        this.collectRunner = collectRunner;
    }

    @XxlJob("minuteCollectJobHandler")
    public void minuteCollect() {
        int idx = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        List<CollectTargetVo> shard = instanceService.listByShard(idx, total);
        XxlJobHelper.log("分片 {}/{} 待采集实例数: {}", idx, total, shard.size());
        collectRunner.run(shard, CollectFrequency.MINUTE);
    }
}
