package com.ykx.backend.model.vo.user;

import lombok.Data;

import java.util.Date;

@Data
public class UsersRegisterVO {
    // 用户信息
    private String uuid;
    private String username;
    private String email;
    private Date date_joined;
}