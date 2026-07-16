package com.lzzh.monitor.collector.mysql.version;

/** MySQL 8.4 LTS 显式适配器，核心查询沿用 8.0 语法，后续 8.4 差异在此声明。 */
public class MySql84Adapter extends MySql80Adapter {
    @Override
    public String version() {
        return "8.4";
    }
}
