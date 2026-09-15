package com.smartstorm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮箱验证码。按 (email, purpose) 唯一，重发覆盖旧码。
 */
@Data
@TableName("email_code")
public class EmailCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收邮箱 */
    private String email;

    /** 验证码（6 位数字） */
    private String code;

    /** 用途：register */
    private String purpose;

    private LocalDateTime sendTime;

    private LocalDateTime expireTime;
}
