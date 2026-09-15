-- =====================================================================
-- SmartStorm 数据库结构（幂等：每次启动执行，已存在则跳过）
-- 与实体类对应：Room / Note / Member / OpLog
-- =====================================================================

CREATE TABLE IF NOT EXISTS `room` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_code`  VARCHAR(16)  NOT NULL COMMENT '房间号（6 位，用于分享加入）',
    `name`       VARCHAR(128) DEFAULT '头脑风暴' COMMENT '房间名',
    `owner_id`   BIGINT       DEFAULT NULL COMMENT '房主用户 id',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_code` (`room_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='房间';

CREATE TABLE IF NOT EXISTS `note` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_id`    BIGINT       NOT NULL COMMENT '所属房间',
    `user_id`    BIGINT       DEFAULT 0 COMMENT '创建者用户 id',
    `x`          DOUBLE       DEFAULT 0 COMMENT '画布 x 坐标',
    `y`          DOUBLE       DEFAULT 0 COMMENT '画布 y 坐标',
    `width`      DOUBLE       DEFAULT 160 COMMENT '宽',
    `height`     DOUBLE       DEFAULT 100 COMMENT '高',
    `color`      VARCHAR(16)  DEFAULT 'yellow' COMMENT '便利贴颜色',
    `content`    TEXT         COMMENT '内容',
    `z_index`    INT          DEFAULT 0 COMMENT '层级',
    `deleted`    TINYINT      DEFAULT 0 COMMENT '逻辑删除 0/1',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_room_deleted` (`room_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='便利贴';

CREATE TABLE IF NOT EXISTS `member` (
    `id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_id`   BIGINT       NOT NULL COMMENT '所属房间',
    `user_id`   BIGINT       NOT NULL COMMENT '用户 id',
    `user_name` VARCHAR(64)  DEFAULT '访客' COMMENT '昵称',
    `color`     VARCHAR(16)  DEFAULT 'blue' COMMENT '头像颜色标识',
    `joined_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_user` (`room_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='房间成员';

CREATE TABLE IF NOT EXISTS `op_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_id`    BIGINT       NOT NULL COMMENT '所属房间',
    `user_id`    BIGINT       NOT NULL COMMENT '操作用户',
    `seq`        BIGINT       NOT NULL COMMENT '房间内单调递增操作序号',
    `type`       VARCHAR(32)  NOT NULL COMMENT '操作类型：add_note/edit_note/move_note/color_note/delete_note',
    `payload`    TEXT         COMMENT '操作数据 JSON',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_seq` (`room_id`, `seq`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志（回放数据源）';

CREATE TABLE IF NOT EXISTS `user` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `email`        VARCHAR(128) NOT NULL COMMENT '邮箱（登录账号）',
    `password`     VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码哈希',
    `nickname`     VARCHAR(64)  DEFAULT '访客' COMMENT '昵称',
    `avatar_color` VARCHAR(16)  DEFAULT 'blue' COMMENT '头像颜色标识',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

CREATE TABLE IF NOT EXISTS `email_code` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `email`      VARCHAR(128) NOT NULL COMMENT '接收邮箱',
    `code`       VARCHAR(8)   NOT NULL COMMENT '验证码',
    `purpose`    VARCHAR(16)  NOT NULL COMMENT '用途：register',
    `send_time`  DATETIME     NOT NULL COMMENT '发送时间',
    `expire_time` DATETIME    NOT NULL COMMENT '过期时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_purpose` (`email`, `purpose`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='邮箱验证码';

CREATE TABLE IF NOT EXISTS `board_analysis` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_id`     BIGINT       NOT NULL COMMENT '所属房间',
    `user_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '发起人用户 id',
    `note_count`  INT          NOT NULL DEFAULT 0 COMMENT '参与分析的便利贴数',
    `model`       VARCHAR(64)  DEFAULT 'deepseek-v4-flash' COMMENT '使用的模型',
    `result_json` MEDIUMTEXT   COMMENT '分析结果 JSON（groups/conflicts/summary）',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_room_created` (`room_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='智能整理结果（供回放）';

