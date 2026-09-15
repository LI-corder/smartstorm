package com.smartstorm.controller;

import com.smartstorm.common.Result;
import com.smartstorm.common.UnauthorizedException;
import com.smartstorm.dto.LoginRequest;
import com.smartstorm.dto.LoginResponse;
import com.smartstorm.dto.RegisterRequest;
import com.smartstorm.dto.ResetPasswordRequest;
import com.smartstorm.dto.SendCodeRequest;
import com.smartstorm.dto.UserVO;
import com.smartstorm.entity.User;
import com.smartstorm.service.AuthService;
import com.smartstorm.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：发送验证码 / 注册 / 登录 / 当前用户。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    /** 发送注册验证码 */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest req) {
        authService.sendCode(req.getEmail());
        return Result.ok();
    }

    /** 发送重置密码验证码 */
    @PostMapping("/send-reset-code")
    public Result<Void> sendResetCode(@Valid @RequestBody SendCodeRequest req) {
        authService.sendResetCode(req.getEmail());
        return Result.ok();
    }

    /** 重置密码 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return Result.ok();
    }

    /** 注册（注册即登录，返回 token） */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    /** 当前登录用户信息（带 Bearer token 访问） */
    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        User user = authService.getById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在");
        }
        return Result.ok(UserVO.from(user));
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return jwtUtil.parseUserId(authHeader.substring(7));
    }
}
