package com.lzzh.monitor.collector.postgresql.version;

import com.lzzh.monitor.collector.spi.version.VersionResolver;
import org.springframework.stereotype.Component;

/** PostgreSQL 版本解析器：注册 13（通配基线），14/15/16 按 floor 回退命中。 */
@Component
public class PgVersionResolver {

    private final VersionResolver<PgVersionAdapter> delegate = new VersionResolver<>();

    public PgVersionResolver() {
        delegate.register(new Pg13Adapter());
        delegate.register(new Pg16Adapter());
        delegate.register(new Pg17Adapter());
        delegate.register(new Pg18Adapter());
    }

    public PgVersionAdapter resolve(String version) {
        return delegate.resolve(version);
    }
}
