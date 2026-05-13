package com.ykx.backend.intercepter;

import com.ykx.backend.chat.ChatRateLimiter;
import com.ykx.backend.common.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 在登录通过后对 /chat/** 做按用户限流（具体策略见 {@link ChatRateLimiter}）。
 */
@Component
public class ChatRateLimitInterceptor implements HandlerInterceptor {

    @Resource
    private ChatRateLimiter chatRateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String userId = UserContext.getUserId();
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/chat/document")) {
            chatRateLimiter.acquireDocument(userId);
        } else {
            chatRateLimiter.acquireSend(userId);
        }
        return true;
    }
}
