package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 主机磁盘 IO 按盘趋势响应：每盘一组繁忙度 / 读速率 / 写速率序列。 */
@Data
@Schema(description = "主机磁盘 IO 按盘趋势响应")
public class HostDiskIoTrendVo {

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "按盘序列（盘名字典序）")
    private List<DeviceSeries> devices;

    /** 单盘的三组趋势序列。 */
    @Data
    @Schema(description = "单盘磁盘 IO 趋势序列")
    public static class DeviceSeries {

        @Schema(description = "盘名（Linux 块设备如 sda / Windows 盘符如 C:）")
        private String device;

        @Schema(description = "IO 繁忙度序列（%）")
        private List<MetricTrendVo.Point> util;

        @Schema(description = "读速率序列（B/s）")
        private List<MetricTrendVo.Point> read;

        @Schema(description = "写速率序列（B/s）")
        private List<MetricTrendVo.Point> write;
    }
}
