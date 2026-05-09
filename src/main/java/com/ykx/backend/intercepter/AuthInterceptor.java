package com.ykx.backend.intercepter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWTUtil;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@Component
public class AuthInterceptor implements HandlerInterceptor {

    // 你的 JWT 密钥（和业务代码一致）
    private static final String JWT_SECRET = "Ykx_JWT_Secret_2025_123456789";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 从请求头获取 token
        String token = request.getHeader("Authorization");

        // 2. 如果没有 token，直接拒绝
        if (StrUtil.isBlank(token) || !token.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }

        // 3. 去掉 Bearer 前缀
        String accessToken = token.replace("Bearer ", "");

        // 4. 统一校验 Token
        boolean isValid;
        try {
            isValid = JWTUtil.verify(accessToken, JWT_SECRET.getBytes());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "token 非法");
        }
        if (!isValid) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "token 已过期");
        }

        // 5. 解析出 userId，存入上下文
        String userId = (String) JWTUtil.parseToken(accessToken).getPayload("userId");
        UserContext.setUserId(userId);

        // 6. 放行
        return true;
    }

    // 请求结束后清除上下文
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
