package com.ykx.backend.model.vo.user;

import lombok.Data;

import java.util.Date;

/**
 * 用户表
 * @TableName users
 */
@Data
public class UsersUpdateVO {
    /**
     * 用户唯一标识
     */
    private String uuid;

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
     * 状态 0-正常 1-禁用
     */
    private Integer status;

    /**
     * 性别 0-未知 1-男 2-女
     */
    private Integer gender;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 权限：0-普通用户 1-管理员
     */
    private Integer role;

    /**
     * 注册时间
     */
    private Date date_joined;

    /**
     * 最后登录时间
     */
    private Date last_login;

}