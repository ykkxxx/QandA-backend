package com.ykx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 聊天与知识库文档上传、限流参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.chat")
public class ChatAppProperties {

    /** 每用户每分钟允许「发送聊天」次数 */
    private int ratePerMinute = 30;

    /** 每用户每分钟允许「文档入库」次数 */
    private int documentRatePerMinute = 8;

    /** 文档临时/持久保存目录（相对工作目录） */
    private String documentUploadDir = "uploads/kb-docs";

    /** 单文件最大字节（txt/pdf） */
    private long documentMaxBytes = 10 * 1024 * 1024L;
}
