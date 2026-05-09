package com.ykx.backend.model.dto;

import lombok.Data;

@Data
public class UsersRegisterDTO {
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;
    /**
     * 确认密码
     */
    private String confirm_password;
    /**
     * 手机号
     */
    private String telephone;
    /**
     * 邮箱
     */
    private String email;

}
