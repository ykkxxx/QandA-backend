package com.ykx.backend.intercepter;

import cn.hutool.core.util.StrUtil;
import com.ykx.backend.config.AdminProperties;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Admin-Token";

    @Resource
    private AdminProperties adminProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(HEADER_NAME);
        String configuredToken = adminProperties.getToken();

        // 添加调试日志
        System.out.println("=== 调试信息 ===");
        System.out.println("adminProperties 对象: " + adminProperties);
        System.out.println("配置的 token: [" + configuredToken + "]");
        System.out.println("配置的 token 长度: " + (configuredToken == null ? "null" : configuredToken.length()));
        System.out.println("请求头 token: [" + token + "]");
        System.out.println("=================");
        if (StrUtil.isBlank(adminProperties.getToken()) || !adminProperties.getToken().equals(token)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无效的管理员凭证");
        }
        return true;
    }
}
