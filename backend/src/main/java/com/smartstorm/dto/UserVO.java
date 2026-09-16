package com.smartstorm.dto;

import com.smartstorm.entity.User;
import lombok.Data;

/**
 * 用户视图对象（返回给前端，不含密码）。
 */
@Data
public class UserVO {

    private Long id;
    private String email;
    private String nickname;
    private String avatarColor;

    /** 自定义头像 URL；为 null 表示未上传，前端回退到颜色块 */
    private String avatarUrl;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatarColor(user.getAvatarColor());
        vo.setAvatarUrl(user.getAvatarUrl());
        return vo;
    }
}
