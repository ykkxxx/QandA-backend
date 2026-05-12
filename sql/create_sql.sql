CREATE TABLE users (
                       uuid           VARCHAR(32)    PRIMARY KEY  COMMENT '用户唯一标识',
                       username       VARCHAR(150)   NOT NULL     COMMENT '用户名',
                       email          VARCHAR(191)   NOT NULL     UNIQUE COMMENT '邮箱',
                       telephone      VARCHAR(20)    UNIQUE       COMMENT '手机号',
                       password_hash  VARCHAR(255)   NOT NULL     COMMENT '加密密码',
                       status         TINYINT        NOT NULL     DEFAULT 1 COMMENT '状态 1-正常 0-封禁（与后端 UserStatusConstants 一致）',
                       gender         TINYINT                   NULL COMMENT '性别 0-未知 1-男 2-女',
                       bio            TEXT                      NULL COMMENT '个人简介',
                       avatar         VARCHAR(255)              NULL COMMENT '头像地址',
                       date_joined    DATETIME      NOT NULL     COMMENT '注册时间',
                       last_login     DATETIME                 NULL COMMENT '最后登录时间',
                       is_delete      TINYINT        NOT NULL     DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE `chat_sessions` (
                                 `id` VARCHAR(64) NOT NULL,
                                 `user_id` VARCHAR(64) NOT NULL,
                                 `title` VARCHAR(255) DEFAULT '新的对话',
                                 `metadata` JSON NULL,
                                 `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                 `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                 PRIMARY KEY (`id`),
                                 KEY `idx_chat_sessions_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `chat_messages` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `session_id` VARCHAR(64) NOT NULL,
                                 `role` VARCHAR(32) NOT NULL,
                                 `content` TEXT NOT NULL,
                                 `metadata` JSON NULL,
                                 `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                 PRIMARY KEY (`id`),
                                 KEY `idx_chat_messages_session_id` (`session_id`),
                                 CONSTRAINT `fk_chat_messages_session_id`
                                     FOREIGN KEY (`session_id`) REFERENCES `chat_sessions` (`id`)
                                         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;