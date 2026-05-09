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
        String token = request.getHeader(HEADER_NAME);
        if (StrUtil.isBlank(adminProperties.getToken()) || !adminProperties.getToken().equals(token)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无效的管理员凭证");
        }
        return true;
    }
}
