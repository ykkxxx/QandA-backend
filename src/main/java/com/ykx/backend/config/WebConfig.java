package com.ykx.backend.config;

import com.ykx.backend.intercepter.AdminTokenInterceptor;
import com.ykx.backend.intercepter.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminTokenInterceptor adminTokenInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, AdminTokenInterceptor adminTokenInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminTokenInterceptor = adminTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/admin/**");

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/refresh",
                        "/user/sso/exchange",
                        "/admin/**",
                        "/health"
                );
    }
}