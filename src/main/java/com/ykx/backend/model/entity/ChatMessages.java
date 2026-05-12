package com.ykx.backend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class ChatMessages {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 关键字段：会话id
     */
    private String session_id;

    private String user_id;

    /**
     * user / assistant
     */
    private String role;

    private String content;

    private LocalDateTime created_at;

    @TableLogic
    private Integer is_delete;
}