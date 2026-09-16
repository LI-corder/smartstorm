package com.smartstorm.controller;

import com.smartstorm.common.Result;
import com.smartstorm.common.UnauthorizedException;
import com.smartstorm.dto.ChangePasswordRequest;
import com.smartstorm.dto.ProfileVO;
import com.smartstorm.dto.UpdateProfileRequest;
import com.smartstorm.dto.UserVO;
import com.smartstorm.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心接口（均需登录）：
 * GET    /api/profile                 个人中心聚合数据
 * PUT    /api/profile                 编辑昵称 / 头像颜色
 * POST   /api/profile/avatar          上传自定义头像（multipart，字段名 file）
 * DELETE /api/profile/avatar          移除自定义头像
 * POST   /api/profile/change-password 修改密码
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public Result<ProfileVO> getProfile() {
        return Result.ok(profileService.overview(currentUserId()));
    }

    @PutMapping
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return Result.ok(profileService.updateProfile(currentUserId(), req));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        profileService.changePassword(currentUserId(), req);
        return Result.ok();
    }

    /** 上传自定义头像（multipart，字段名 file）。选好即生效，不需要再点保存资料 */
    @PostMapping("/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(profileService.uploadAvatar(currentUserId(), file));
    }

    /** 移除自定义头像，回退到颜色块 */
    @DeleteMapping("/avatar")
    public Result<UserVO> removeAvatar() {
        return Result.ok(profileService.removeAvatar(currentUserId()));
    }

    /** 当前登录用户 id（JWT 过滤器写入 SecurityContext；取不到说明未认证） */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new UnauthorizedException("未登录");
    }
}
