package com.ykx.backend.common;

public class UserContext {
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE_HOLDER = new ThreadLocal<>();

    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    /** 当前请求用户 {@code users.role}，未设置时为 null */
    public static void setRole(Integer role) {
        ROLE_HOLDER.set(role);
    }

    public static Integer getRole() {
        return ROLE_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
        ROLE_HOLDER.remove();
    }
}
