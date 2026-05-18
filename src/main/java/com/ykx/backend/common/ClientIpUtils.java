package com.ykx.backend.common;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
//用来从 HTTP 请求里，拿到用户的【真实 IP 地址】。

// 工具类：获取客户端真实IP
public final class ClientIpUtils {

    // 构造方法私有，不让别人new对象（标准工具类写法）
    private ClientIpUtils() {}

    // 核心方法：传入request，返回真实IP
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        // 1. 先取 X-Forwarded-For 请求头（最常用，多层代理都会带）
        // 格式：用户IP, 代理1, 代理2...
        String xff = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(xff)) {
            return xff.split(",")[0].trim(); // 取第一个，就是真实IP
        }

        // 2. 如果上面没有，取 X-Real-IP（Nginx 常用）
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) {
            return realIp.trim();
        }

        // 3. 最后兜底：直接拿远程地址（最原始，但可能是代理IP）
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
