package com.lzzh.monitor.collector.postgresql.version;

import com.lzzh.monitor.collector.spi.version.VersionResolver;
import org.springframework.stereotype.Component;

/** PostgreSQL 版本解析器：显式注册产品支持的 14～18，并保留 13 作为 SQL 基线。 */
@Component
public class PgVersionResolver {

    private final VersionResolver<PgVersionAdapter> delegate = new VersionResolver<>();

    public PgVersionResolver() {
        delegate.register(new Pg13Adapter());
        delegate.register(new Pg14Adapter());
        delegate.register(new Pg15Adapter());
        delegate.register(new Pg16Adapter());
        delegate.register(new Pg17Adapter());
        delegate.register(new Pg18Adapter());
    }

    public PgVersionAdapter resolve(String version) {
        return delegate.resolve(version);
    }
}
