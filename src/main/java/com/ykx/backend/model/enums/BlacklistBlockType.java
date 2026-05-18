package com.ykx.backend.model.enums;

import lombok.Getter;

@Getter
public enum BlacklistBlockType {
    /**
     * 封 账号（唯一ID，最稳）
     * 用途：注销、永久封号、严重违规
     */
    USER_ID("USER_ID"),

    /**
     * 封 IP（防刷、爬虫、暴力破解）
     */
    IP("IP");


    private final String code;

    BlacklistBlockType(String code) {
        this.code = code;
    }
}
