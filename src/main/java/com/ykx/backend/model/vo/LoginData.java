package com.ykx.backend.model.vo;

import lombok.Data;

@Data
public class LoginData<T> {
    private String access_token;    // 1. 接口访问令牌
    private String refresh_token;   // 2. 刷新令牌
    //access_token 还有多少秒过期
    private Integer expires_in;     // 3. 过期时间（秒）
    private String token_type;      // 4. 令牌类型（固定Bearer）
    private T user;                 // 5. 用户信息
}
