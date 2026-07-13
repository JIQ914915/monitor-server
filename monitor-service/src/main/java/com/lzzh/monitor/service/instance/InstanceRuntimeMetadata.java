package com.lzzh.monitor.service.instance;

/** 单实例接口使用的服务端权威数据库类型与版本元数据。 */
public record InstanceRuntimeMetadata(
        Long instanceId,
        Long dbTypeId,
        Long dbVersionId,
        String dbTypeCode,
        String dbTypeLabel,
        String dbVersion,
        String driverClass,
        String urlTemplate) {
}