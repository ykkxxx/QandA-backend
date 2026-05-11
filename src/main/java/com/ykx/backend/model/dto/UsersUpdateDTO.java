package com.ykx.backend.model.dto;

import lombok.Data;

@Data
public class UsersUpdateDTO {
    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String telephone;

    /**
     * 性别 0-未知 1-男 2-女
     */
    private Integer gender;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 头像访问地址（外链 URL 等）；若本次请求同时上传了头像文件，以文件为准，忽略本字段。
     */
    private String avatar;
}
