package com.smartstorm.controller;

import com.smartstorm.service.AvatarStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 头像图片读取（匿名可访问）。
 *
 * <p>路径放在 {@code /api} 下，是为了复用前端 dev 已有的 {@code /api} 代理与
 * {@code CorsConfig} 的跨域规则 —— 否则要额外加静态资源映射、再给 vite 加一条代理，
 * 两处配置面。图片要能在 {@code <img src>} 里直接加载，所以不能要求登录。</p>
 *
 * <p>文件名由 {@code AvatarStorageService} 用正则严格校验，只有本服务生成过的形式才放行，
 * 因此不存在路径穿越。</p>
 */
@RestController
@RequestMapping("/api/avatars")
public class AvatarController {

    private final AvatarStorageService storage;

    public AvatarController(AvatarStorageService storage) {
        this.storage = storage;
    }

    /**
     * 返回头像图片。
     *
     * <p>{@code {filename:.+}} 里的正则是必须的 —— 否则路径变量可能被当成文件后缀截断。</p>
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> get(@PathVariable String filename) {
        Path path = storage.resolve(filename);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AvatarStorageService.contentTypeOf(filename)))
                // 文件名是 UUID，内容永不改变，可以放心长缓存
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                // 提供的是用户上传的内容，禁止浏览器嗅探类型
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
