package com.smartstorm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（注册账号）。email 为唯一登录账号，password 为 BCrypt 哈希。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 邮箱（登录账号，唯一） */
    private String email;

    /** BCrypt 密码哈希 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像颜色标识（未上传图片时的回退显示） */
    private String avatarColor;

    /** 自定义头像 URL（如 /api/avatars/xxx.png）；为 null 表示未上传，回退到颜色块 */
    private String avatarUrl;

    private LocalDateTime createdAt;
}
