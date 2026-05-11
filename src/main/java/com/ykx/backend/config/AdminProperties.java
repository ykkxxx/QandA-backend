package com.ykx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {
    /**
     * 管理接口密钥，请求头 X-Admin-Token 需与此一致
     */
    private String token;
}
