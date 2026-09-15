package com.smartstorm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料修改请求（昵称 + 头像颜色；邮箱不可修改）。
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称最长 32 位")
    private String nickname;

    @NotBlank(message = "头像颜色不能为空")
    private String avatarColor;
}
