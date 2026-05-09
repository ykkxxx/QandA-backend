package com.ykx.backend.model.dto;

import lombok.Data;

@Data
public class UsersLoginDTO {
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;

}
