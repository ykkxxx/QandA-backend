package com.ykx.backend.model.vo.user;

import lombok.Data;

@Data
public class UsersLoginVO {
    private String uuid;
    private String username;
    private String avatar;
    /** 权限：0-普通用户 1-管理员 */
    private Integer role;
}