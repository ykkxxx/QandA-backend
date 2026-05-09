package com.ykx.backend.model.enums;

import lombok.Getter;

@Getter
public enum BlacklistBlockType {
    USER_ID("USER_ID"),
    IP("IP"),
    USERNAME("USERNAME");

    private final String code;

    BlacklistBlockType(String code) {
        this.code = code;
    }
}
