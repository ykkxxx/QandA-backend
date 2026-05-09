package com.ykx.backend.intercepter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWTUtil;
import com.ykx.backend.common.ClientIpUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.common.UserStatusConstants;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.entity.Users;
import com.ykx.backend.service.BlacklistService;
import com.ykx.backend.service.UsersService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 与 UsersServiceImpl 中会话绑定一致：仅存当前生效的 access_token */
    private static final String USER_LATEST_ACCESS_PREFIX = "user:token:";

    // 你的 JWT 密钥（和业务代码一致）
    private static final String JWT_SECRET = "Ykx_JWT_Secret_2025_123456789";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UsersService usersService;

    @Resource
    private BlacklistService blacklistService;

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

        String clientIp = ClientIpUtils.resolve(request);
        if (StrUtil.isNotBlank(clientIp) && blacklistService.isBlockedIp(clientIp)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "当前网络环境暂时无法访问");
        }
        if (blacklistService.isBlockedUserId(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该账号已被封禁，禁止访问");
        }

        Users user = usersService.getById(userId);
        if (user == null || !UserStatusConstants.isNormal(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该账号已被封禁，禁止访问");
        }

        // 单点登录校验

        try {
            //// 从Redis拿到当前账号最新登录token
            String latest = stringRedisTemplate.opsForValue().get(USER_LATEST_ACCESS_PREFIX + userId);
            /*
             * 判定规则：
             * 1.latest为空：说明主动退出/管理员封禁删除了token
             * 2.不一致：说明异地登录，旧token被覆盖
             * 两种情况全部强制下线
             */
            if (StrUtil.isBlank(latest) || !latest.equals(accessToken)) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "账号已在其他设备登录，请重新登录");
            }
        } catch (BusinessException e) {
            // 业务异常直接抛出（被挤下线、退出登录）
            throw e;
        } catch (Exception e) {
            // Redis断线、网络波动、连接失败兜底异常
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话校验失败");
        }

        return true;
    }

    // 请求结束后清除上下文
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
