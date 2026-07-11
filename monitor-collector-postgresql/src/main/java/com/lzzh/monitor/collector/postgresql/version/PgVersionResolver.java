package com.lzzh.monitor.collector.postgresql.version;

import com.lzzh.monitor.collector.spi.version.VersionResolver;
import org.springframework.stereotype.Component;

/** PostgreSQL 版本解析器：注册 13（通配基线），14/15/16 按 floor 回退命中。 */
@Component
public class PgVersionResolver {

    private final VersionResolver<PgVersionAdapter> delegate = new VersionResolver<>();

    public PgVersionResolver() {
        delegate.register(new Pg13Adapter());
        // 新版本出现字段差异时：new Pg17Adapter() 在此注册
    }

    public PgVersionAdapter resolve(String version) {
        return delegate.resolve(version);
    }
}
