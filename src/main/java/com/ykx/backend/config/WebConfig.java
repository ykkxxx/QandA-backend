package com.ykx.backend.config;

import com.ykx.backend.intercepter.AdminTokenInterceptor;
import com.ykx.backend.intercepter.AuthInterceptor;
import com.ykx.backend.intercepter.ChatRateLimitInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Resource
    private AdminTokenInterceptor adminTokenInterceptor;

    @Resource
    private UploadProperties uploadProperties;

    @Resource
    private ChatRateLimitInterceptor chatRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 1. 管理员拦截器（只管后台）
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/admin/security/**");

        // 2. 登录拦截器（只拦截需要登录的接口）
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/user/**",
                        "/session/**",
                        "/message/**",
                        "/vector/**",
                        "/rag/**",
                        "/chat/**"
                )
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/refresh",
                        "/user/sso/exchange",
                        "/files/**"
                )
                .order(1);

        // 3. 聊天接口 Redisson 限流（须在登录之后，依赖 UserContext）
        registry.addInterceptor(chatRateLimitInterceptor)
                .addPathPatterns("/chat/send", "/chat/document")
                .order(2);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadProperties.getAvatarDir()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/files/avatars/**")
                .addResourceLocations(location);
    }
}