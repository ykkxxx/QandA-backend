package com.ykx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek AI 配置类
 * 读取yml配置，避免硬编码密钥
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekAiProperties {
    /**
     * 密钥
     */
    private String apiKey;
    /**
     * 接口地址
     */
    private String apiUrl;
    /**
     * 模型名称
     */
    private String model;
}

