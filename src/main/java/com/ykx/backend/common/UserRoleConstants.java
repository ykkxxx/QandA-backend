package com.ykx.backend.common;

/**
 * 与 {@code users.role} 整型约定一致（可按产品调整数值，须与库表默认/迁移脚本对齐）。
 */
public final class UserRoleConstants {

    /** 普通用户 */
    public static final int USER = 0;
    /** 管理员（具备更高权限时在后端再校验此值） */
    public static final int ADMIN = 1;

    private UserRoleConstants() {
    }

    public static boolean isAdmin(Integer role) {
        return role != null && role == ADMIN;
    }
}
