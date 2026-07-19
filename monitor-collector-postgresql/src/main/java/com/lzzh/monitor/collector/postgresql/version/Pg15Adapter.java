package com.lzzh.monitor.collector.postgresql.version;

/** PostgreSQL 15 适配器。 */
public class Pg15Adapter extends Pg14Adapter {
    @Override public String version() { return "15"; }
}
