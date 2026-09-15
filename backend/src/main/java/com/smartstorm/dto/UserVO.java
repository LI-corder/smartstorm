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

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatarColor(user.getAvatarColor());
        return vo;
    }
}
