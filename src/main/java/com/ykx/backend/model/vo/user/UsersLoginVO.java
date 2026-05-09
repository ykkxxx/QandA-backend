package com.ykx.backend.model.vo.user;

import lombok.Data;

@Data
public class UsersLoginVO {
    // 用户信息
    private String uuid;
    private String username;
    private String avatar;
}