package com.ykx.backend.model.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class ChatMessageVO {
    /**
     * 消息主键ID
     */
    private String id;

    /**
     * 角色：user / assistant
     */
    private String role;

    /**
     * 聊天内容
     */
    private String content;

    /**
     * 扩展元数据JSON
     */
    private Object metadata;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
