package com.smartstorm.dto;

import lombok.Data;

/**
 * 登录/注册成功响应：JWT token + 用户信息。
 */
@Data
public class LoginResponse {

    private String token;
    private UserVO user;

    public static LoginResponse of(String token, UserVO user) {
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUser(user);
        return resp;
    }
}
