package com.ykx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /**
     * 头像保存目录（相对路径相对进程工作目录，建议使用绝对路径部署）
     */
    //头像上传的路径
    private String avatarDir = "uploads/avatars";

    /**
     * 头像文件最大字节数，≤0 表示不限制（仍受 spring.servlet.multipart 限制）
     */
    private long maxAvatarBytes = 2 * 1024 * 1024;
}
