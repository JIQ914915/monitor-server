package com.lzzh.monitor.collector.mysql.version;

import com.lzzh.monitor.collector.spi.version.VersionResolver;
import org.springframework.stereotype.Component;

/** MySQL 版本解析器：注册 5.6/5.7/8.0/8.4，解析采用精确→就近向下回退。 */
@Component
public class MySqlVersionResolver {

    private final VersionResolver<MySqlVersionAdapter> delegate = new VersionResolver<>();

    public MySqlVersionResolver() {
        delegate.register(new MySql56Adapter());
        delegate.register(new MySql57Adapter());
        delegate.register(new MySql80Adapter());
        delegate.register(new MySql84Adapter());
    }

    public MySqlVersionAdapter resolve(String version) {
        return delegate.resolve(version);
    }
}
