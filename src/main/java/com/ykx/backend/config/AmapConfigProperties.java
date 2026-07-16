package com.ykx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "amap")
@Data
public class AmapConfigProperties {
    /** 高德Web服务API Key */
    private String apiKey;

    /** 接口基础域名 */
    private String baseUrl;

    /** 安全密钥（可选） */
    private String secret;
}
