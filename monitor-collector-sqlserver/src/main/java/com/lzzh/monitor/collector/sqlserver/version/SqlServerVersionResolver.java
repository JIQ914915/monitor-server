package com.lzzh.monitor.collector.sqlserver.version;

import com.lzzh.monitor.collector.spi.version.VersionResolver;
import org.springframework.stereotype.Component;

/** SQL Server 正式支持版本解析器。 */
@Component
public class SqlServerVersionResolver {
    private final VersionResolver<SqlServerVersionAdapter> delegate = new VersionResolver<>();

    public SqlServerVersionResolver() {
        delegate.register(new SqlServer2012Adapter());
        delegate.register(new SqlServer2014Adapter());
        delegate.register(new SqlServer2016Adapter());
        delegate.register(new SqlServer2017Adapter());
        delegate.register(new SqlServer2019Adapter());
        delegate.register(new SqlServer2022Adapter());
        delegate.register(new SqlServer2025Adapter());
    }

    public SqlServerVersionAdapter resolve(String version) {
        if (version == null || !version.matches("^(2012|2014|2016|2017|2019|2022|2025)(\\..*)?$")) {
            return null;
        }
        return delegate.resolve(version);
    }
}
