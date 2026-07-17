package com.lzzh.monitor.collector.connection;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.common.datatype.JdbcUrlTemplate;
import com.lzzh.monitor.collector.spi.model.TargetDataSource;
import org.springframework.util.StringUtils;

/** 根据数据库类型配置构建目标连接；不在运行期猜测数据库驱动。 */
public final class TargetDataSourceFactory {

    private TargetDataSourceFactory() {
    }

    public static TargetDataSource from(CollectTargetVo target) {
        if (!StringUtils.hasText(target.getUrlTemplate())) {
            throw new IllegalStateException("实例 " + target.getId() + " 对应数据库类型未配置 url_template");
        }
        if (!StringUtils.hasText(target.getDriverClass())) {
            throw new IllegalStateException("实例 " + target.getId() + " 对应数据库类型未配置 driver_class");
        }
        TargetDataSource dataSource = new TargetDataSource();
        dataSource.setJdbcUrl(JdbcUrlTemplate.render(target.getUrlTemplate(),
                target.getHost(), target.getPort(), target.getDatabaseName()));
        dataSource.setUsername(target.getConnUser());
        dataSource.setPassword(target.getConnPassword());
        dataSource.setDriverClass(target.getDriverClass());
        return dataSource;
    }
}