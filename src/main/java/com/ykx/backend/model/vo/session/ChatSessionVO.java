package com.ykx.backend.model.vo.session;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;
//全局的
@Data
public class ChatSessionVO {
    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 最后更新时间
     */
    private Date updatedAt;
}
