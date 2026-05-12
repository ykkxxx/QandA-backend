package com.ykx.backend.model.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
//会话--消息（多）
@Data
public class ChatSessionHistoryVO {
    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 所属用户ID
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 会话扩展元数据
     */
    private Object metadata;

    /**
     * 会话创建时间
     */
    private Date createdAt;

    /**
     * 会话更新时间
     */
    private Date updatedAt;

    /**
     * 当前会话下所有聊天消息列表
     */
    private List<ChatMessageVO> messageList;
}
