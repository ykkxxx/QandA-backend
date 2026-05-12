package com.ykx.backend.model.vo.session;


import lombok.Data;

/**
 * 发送消息接口 返回DTO
 * 对应文档：data:{sessionId,content}
 */
@Data
public class MessageSendResponseVO {

    /**
     * 当前会话id
     */
    private String sessionId;

    /**
     * AI返回回答内容
     */
    private String content;
}

