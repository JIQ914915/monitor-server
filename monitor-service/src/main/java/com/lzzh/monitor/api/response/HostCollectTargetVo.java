package com.lzzh.monitor.api.response;

import lombok.Data;

import java.util.List;

/** 主机采集目标（仅供 collector 内部使用）。 */
@Data
public class HostCollectTargetVo {

    private Long id;
    private String hostCode;
    private String name;
    private String ip;
    /** exporter / ssh / none。 */
    private String collectMode;
    private Integer exporterPort;
    private String exporterPath;
    /** normal / abnormal / paused。 */
    private String status;
    /** 关联实例 ID 列表（指标扇出写入目标）。 */
    private List<Long> instanceIds;
}
