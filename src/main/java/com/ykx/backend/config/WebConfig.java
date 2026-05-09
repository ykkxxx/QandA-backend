package com.ykx.backend.config;

import com.ykx.backend.intercepter.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")          // 对所有接口生效
                .excludePathPatterns(
                        "/api/user/login",           // 登录放行
                        "/api/user/register",        // 注册放行
                        "/api/user/refresh"          //刷新token
                );
    }
}