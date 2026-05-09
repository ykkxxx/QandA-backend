package com.ykx.backend.model.dto;

import lombok.Data;

@Data
public class UsersUpdateDTO {
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;
    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String telephone;

}
