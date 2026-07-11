package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.enums.MetricRole;

import java.util.List;

/** 数据库类型到物理指标编码的静态目录。 */
interface DatabaseMetricCatalog {
    DbType supportedType();
    String codeOf(MetricRole role);
    List<String> numericParameterCodes();
    List<String> textParameterCodes();
    String numericParameterPrefix();
    String textParameterPrefix();
}