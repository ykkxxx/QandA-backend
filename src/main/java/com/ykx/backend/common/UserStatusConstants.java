package com.ykx.backend.common;

/**
 * 与业务约定一致：1=正常，0=封禁（与注册时 {@code setStatus(1)} 一致）
 */
public final class UserStatusConstants {

    public static final int NORMAL = 1;
    public static final int BANNED = 0;

    private UserStatusConstants() {
    }

    public static boolean isNormal(Integer status) {
        return Integer.valueOf(NORMAL).equals(status);
    }
}
