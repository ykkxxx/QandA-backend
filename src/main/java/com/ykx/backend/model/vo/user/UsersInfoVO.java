package com.ykx.backend.model.vo.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 用户表
 * @TableName users
 */
@Data
public class UsersInfoVO {
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
     * 注册时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date date_joined;

    /**
     * 最后登录时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date last_login;

}