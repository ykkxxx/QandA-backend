package com.ykx.backend.model.vo.user;

import lombok.Data;

@Data
public class SsoCodeVO {
    /** 一次性票据，供另一站点换取 token */
    private String code;
    /** 有效时间（秒） */
    private Integer expires_in;
}
