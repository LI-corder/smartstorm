package com.smartstorm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartstorm.common.UnauthorizedException;
import com.smartstorm.dto.LoginRequest;
import com.smartstorm.dto.LoginResponse;
import com.smartstorm.dto.RegisterRequest;
import com.smartstorm.dto.ResetPasswordRequest;
import com.smartstorm.dto.UserVO;
import com.smartstorm.entity.EmailCode;
import com.smartstorm.entity.User;
import com.smartstorm.mapper.EmailCodeMapper;
import com.smartstorm.mapper.UserMapper;
import com.smartstorm.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证服务：邮箱验证码注册 + 登录。
 *
 * <p>验证码规则：6 位数字、5 分钟有效、60 秒防重发、按 (email, purpose) 覆盖旧码。
 * 密码使用 BCrypt 哈希存储。</p>
 */
@Service
public class AuthService {

    private static final String PURPOSE_REGISTER = "register";
    private static final String PURPOSE_RESET = "reset";
    private static final int CODE_TTL_MINUTES = 5;
    private static final long RESEND_INTERVAL_SECONDS = 60;
    private static final String[] AVATAR_COLORS = {"blue", "purple", "green", "orange"};

    private final UserMapper userMapper;
    private final EmailCodeMapper emailCodeMapper;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper,
                       EmailCodeMapper emailCodeMapper,
                       EmailService emailService,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.emailCodeMapper = emailCodeMapper;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // ---------------- 发送验证码 ----------------

    /** 发送注册验证码（邮箱须未注册） */
    public void sendCode(String email) {
        sendCodeInternal(email, PURPOSE_REGISTER, true);
    }

    /** 发送重置密码验证码（邮箱须已注册） */
    public void sendResetCode(String email) {
        sendCodeInternal(email, PURPOSE_RESET, false);
    }

    private void sendCodeInternal(String email, String purpose, boolean requireUnregistered) {
        String normalized = normalizeEmail(email);
        User existing = getByEmail(normalized);
        if (requireUnregistered && existing != null) {
            throw new IllegalArgumentException("该邮箱已注册，请直接登录");
        }
        if (!requireUnregistered && existing == null) {
            throw new IllegalArgumentException("该邮箱未注册");
        }
        EmailCode codeRecord = findByEmailPurpose(normalized, purpose);
        // 60 秒防重发
        if (codeRecord != null) {
            long elapsed = java.time.Duration.between(codeRecord.getSendTime(), LocalDateTime.now()).getSeconds();
            if (elapsed < RESEND_INTERVAL_SECONDS) {
                throw new IllegalArgumentException("发送过于频繁，请 " + (RESEND_INTERVAL_SECONDS - elapsed) + " 秒后再试");
            }
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now();
        EmailCode record = codeRecord != null ? codeRecord : new EmailCode();
        record.setEmail(normalized);
        record.setCode(code);
        record.setPurpose(purpose);
        record.setSendTime(now);
        record.setExpireTime(now.plusMinutes(CODE_TTL_MINUTES));
        if (codeRecord != null) {
            emailCodeMapper.updateById(record);
        } else {
            emailCodeMapper.insert(record);
        }

        emailService.sendCodeMail(normalized, code, purpose);
    }

    // ---------------- 注册（注册即登录） ----------------

    public LoginResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (getByEmail(email) != null) {
            throw new IllegalArgumentException("该邮箱已注册，请直接登录");
        }
        EmailCode record = findByEmailPurpose(email, PURPOSE_REGISTER);
        if (record == null) {
            throw new IllegalArgumentException("请先获取验证码");
        }
        if (record.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!record.getCode().equals(req.getCode().trim())) {
            throw new IllegalArgumentException("验证码错误");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname().trim());
        user.setAvatarColor(AVATAR_COLORS[ThreadLocalRandom.current().nextInt(AVATAR_COLORS.length)]);
        userMapper.insert(user);

        // 验证码一次性使用
        emailCodeMapper.deleteById(record.getId());

        return buildLoginResponse(user);
    }

    // ---------------- 登录 ----------------

    public LoginResponse login(LoginRequest req) {
        String email = normalizeEmail(req.getEmail());
        User user = getByEmail(email);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("邮箱或密码错误");
        }
        return buildLoginResponse(user);
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    // ---------------- 重置密码 ----------------

    public void resetPassword(ResetPasswordRequest req) {
        String email = normalizeEmail(req.getEmail());
        User user = getByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("该邮箱未注册");
        }
        EmailCode record = findByEmailPurpose(email, PURPOSE_RESET);
        if (record == null) {
            throw new IllegalArgumentException("请先获取验证码");
        }
        if (record.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!record.getCode().equals(req.getCode().trim())) {
            throw new IllegalArgumentException("验证码错误");
        }

        user.setPassword(passwordEncoder.encode(req.getPassword()));
        userMapper.updateById(user);
        // 验证码一次性使用
        emailCodeMapper.deleteById(record.getId());
    }

    // ---------------- 内部工具 ----------------

    private LoginResponse buildLoginResponse(User user) {
        String token = jwtUtil.generate(user.getId(), user.getEmail());
        return LoginResponse.of(token, UserVO.from(user));
    }

    private User getByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    private EmailCode findByEmailPurpose(String email, String purpose) {
        return emailCodeMapper.selectOne(new LambdaQueryWrapper<EmailCode>()
                .eq(EmailCode::getEmail, email)
                .eq(EmailCode::getPurpose, purpose));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
