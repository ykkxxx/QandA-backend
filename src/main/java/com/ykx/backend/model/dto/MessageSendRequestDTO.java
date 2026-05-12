package com.ykx.backend.model.dto;
import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 发送消息 请求DTO
 */
@Data
public class MessageSendRequestDTO {

    /**
     * 会话id（可选，不传新建会话）
     */
    private String sessionId;

    /**
     * 用户提问（必填）
     */
    @NotBlank(message = "提问内容不能为空")
    private String query;
}