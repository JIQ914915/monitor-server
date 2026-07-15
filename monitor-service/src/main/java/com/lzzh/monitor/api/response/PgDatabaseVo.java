package com.lzzh.monitor.api.response;

import lombok.Data;

@Data
public class PgDatabaseVo {
    private String name;
    private long sizeBytes;
    private boolean allowConnections;
    private boolean connectable;
    private boolean inScope;
}