package com.ykx.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 1. 覆盖所有请求接口
        registry.addMapping("/**")

                // 2. 允许前端携带 Cookie / Token 凭证
                .allowCredentials(true)

                // 3. 允许所有域名访问（解决跨域最关键）
                .allowedOriginPatterns("*")

                // 4. 允许哪些请求方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")

                // 5. 允许前端携带任何请求头
                .allowedHeaders("*")

                // 6. 允许前端拿到后端返回的所有响应头
                .exposedHeaders("*");
    }
}