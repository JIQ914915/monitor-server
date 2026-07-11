package com.lzzh.monitor.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Web API 启动类。扫描整个产品包，Mapper 单独扫描 dao 模块。 */
@SpringBootApplication(scanBasePackages = "com.lzzh.monitor")
@MapperScan("com.lzzh.monitor.dao.mapper")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
