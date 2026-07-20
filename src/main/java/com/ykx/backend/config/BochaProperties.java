package com.ykx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bocha")
public class BochaProperties {
    private String apiKey;
    private String apiUrl = "https://api.bochaai.com/v1/web-search";
    private Integer count = 5;
}