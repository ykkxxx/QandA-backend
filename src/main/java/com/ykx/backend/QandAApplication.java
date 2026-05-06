package com.ykx.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ykx.backend.mapper")
public class QandAApplication {

    public static void main(String[] args) {
        SpringApplication.run(QandAApplication.class, args);
    }

}
