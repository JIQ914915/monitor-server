package com.lzzh.monitor.collector.job;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.collector.runner.CollectRunner;
import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.service.instance.InstanceService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.util.List;

/** 小时级采集任务（分片广播；采集表大小、慢查询汇总等中低频指标，§14.1）。 */
@Component
public class HourlyCollectJobHandler {

    private final InstanceService instanceService;
    private final CollectRunner collectRunner;

    public HourlyCollectJobHandler(InstanceService instanceService, CollectRunner collectRunner) {
        this.instanceService = instanceService;
        this.collectRunner = collectRunner;
    }

    @XxlJob("hourlyCollectJobHandler")
    public void hourlyCollect() {
        int idx = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        List<CollectTargetVo> shard = instanceService.listByShard(idx, total);
        XxlJobHelper.log("[小时级] 分片 {}/{} 待采集实例数: {}", idx, total, shard.size());
        collectRunner.run(shard, CollectFrequency.HOURLY);
    }
}
