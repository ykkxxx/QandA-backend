package com.ykx.backend.model.vo.user;

import lombok.Data;

import java.util.Date;

@Data
public class UsersRegisterVO {
    private String uuid;
    private String username;
    private String email;
    private Date date_joined;
    /** 权限：0-普通用户 1-管理员 */
    private Integer role;
}