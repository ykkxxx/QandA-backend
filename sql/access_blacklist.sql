-- 访问黑名单：IP / 用户名 / 用户ID（与 users.status 封禁配合）
CREATE TABLE IF NOT EXISTS access_blacklist (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    block_type   VARCHAR(32)  NOT NULL COMMENT 'USER_ID / IP / USERNAME',
    block_value  VARCHAR(255) NOT NULL COMMENT '对应 uuid、IP 或用户名',
    reason       VARCHAR(512)          DEFAULT '' COMMENT '原因说明',
    created_at   DATETIME     NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_type_value (block_type, block_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问黑名单';
