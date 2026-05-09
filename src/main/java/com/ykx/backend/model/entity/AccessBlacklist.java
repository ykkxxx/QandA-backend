package com.ykx.backend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 访问黑名单（IP / 用户名 / 用户ID），与账号封禁配合使用
 */
@TableName(value = "access_blacklist")
@Data
public class AccessBlacklist {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String block_type;
    private String block_value;
    private String reason;
    private Date created_at;
}
