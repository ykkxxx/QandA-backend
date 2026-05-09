package com.ykx.backend;

import com.ykx.backend.config.AdminProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.ykx.backend.mapper")
@EnableConfigurationProperties(AdminProperties.class)
public class QandAApplication {

    public static void main(String[] args) {
        SpringApplication.run(QandAApplication.class, args);
    }

}
