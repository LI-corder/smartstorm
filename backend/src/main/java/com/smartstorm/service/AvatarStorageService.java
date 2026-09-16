package com.smartstorm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 头像文件的存储与读取。
 *
 * <p><b>安全前提：客户端的一切都不可信。</b>文件名、Content-Type、扩展名都是客户端可以
 * 随便编的，所以这里一律：</p>
 * <ul>
 *   <li>按<b>魔数</b>识别真实格式，不认识的直接拒；</li>
 *   <li><b>拒绝 SVG</b> —— SVG 能内嵌 {@code <script>}，而图片是同源提供的，
 *       允许它等于开了存储型 XSS；</li>
 *   <li>文件名由<b>服务端生成</b>（UUID），杜绝 {@code ../../} 之类的路径穿越；</li>
 *   <li>读取时用正则严格校验文件名，只有本服务生成过的形式才放行。</li>
 * </ul>
 *
 * <p>目录默认 {@code ./uploads/avatars}，已被 .gitignore 忽略。</p>
 */
@Service
public class AvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageService.class);

    /** 头像访问路径前缀。走 /api 下是为了复用现有的 vite 代理与 CORS 规则 */
    public static final String URL_PREFIX = "/api/avatars/";

    /** 单张头像大小上限 */
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    /** 最长边像素上限，挡住「解压炸弹」—— 文件很小但解码后极大 */
    private static final int MAX_DIMENSION = 4096;

    /** 本服务生成的文件名形式：32 位 hex + 白名单扩展名。读取时用它挡路径穿越 */
    private static final Pattern SAFE_NAME =
            Pattern.compile("[a-f0-9]{32}\\.(jpg|png|gif|webp)");

    private final Path dir;

    public AvatarStorageService(@Value("${app.upload.avatar-dir:./uploads/avatars}") String dirPath) {
        this.dir = Paths.get(dirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建头像目录：" + dir, e);
        }
        log.info("头像存储目录：{}", dir);
    }

    /**
     * 保存一张头像，返回可直接放进 {@code <img src>} 的相对 URL。
     *
     * @throws IllegalArgumentException 校验不通过（空文件、超限、格式不支持、尺寸过大）
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("图片不能超过 " + (MAX_BYTES / 1024 / 1024) + "MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }

        String ext = detectFormat(bytes);
        if (ext == null) {
            throw new IllegalArgumentException("只支持 JPG / PNG / GIF / WebP 格式的图片");
        }
        checkDimensions(bytes);

        // 文件名由服务端生成，绝不使用客户端传来的名字
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.write(dir.resolve(filename), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("保存头像失败", e);
        }
        return URL_PREFIX + filename;
    }

    /**
     * 删除一个已保存的头像文件。幂等：URL 为空、格式不认识、文件已不存在都不报错。
     *
     * <p>删除失败只记日志不抛异常 —— 用户的资料更新不该因为一个残留文件而失败，
     * 最坏结果只是留个孤儿文件。</p>
     */
    public void delete(String avatarUrl) {
        String filename = filenameOf(avatarUrl);
        if (filename == null) {
            return;
        }
        try {
            if (Files.deleteIfExists(dir.resolve(filename))) {
                log.info("已删除旧头像文件 {}", filename);
            }
        } catch (IOException e) {
            log.warn("删除旧头像文件失败（忽略，可能残留孤儿文件）：{}", filename, e);
        }
    }

    /**
     * 按文件名定位头像文件。
     *
     * @return 可读的文件路径；文件名不合法或文件不存在返回 null
     */
    public Path resolve(String filename) {
        if (filename == null || !SAFE_NAME.matcher(filename).matches()) {
            return null;
        }
        Path path = dir.resolve(filename);
        return Files.isReadable(path) ? path : null;
    }

    /** 扩展名 → MIME，供响应头使用 */
    public static String contentTypeOf(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    // ---------------- 内部 ----------------

    /**
     * 按魔数识别图片格式，返回扩展名；不认识就返回 null。
     *
     * <p><b>故意不支持 SVG</b>：它是 XML，可以内嵌脚本，而同源提供的图片能被浏览器当作
     * 文档执行 —— 那就成了存储型 XSS。黑名单挡不住（比如 svg 改名成 png），
     * 所以这里用白名单：只认下面几种光栅格式。</p>
     */
    private String detectFormat(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (startsWith(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        if (startsWith(b, 0x47, 0x49, 0x46, 0x38)) {
            return "gif";                       // "GIF8"
        }
        // WebP 是 RIFF 容器：前 4 字节 "RIFF"，第 8-11 字节 "WEBP"
        if (startsWith(b, 0x52, 0x49, 0x46, 0x46)
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != (prefix[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 用 ImageIO 读图片头部的宽高。
     *
     * <p>只读头部不整图解码（{@code ImageReader.getWidth/getHeight}），所以既快又能挡住
     * 「解压炸弹」：一个几十 KB 的 PNG 可以声明成十万像素见方，整图解码会吃光内存。</p>
     */
    private void checkDimensions(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) {
                throw new IllegalArgumentException("无法解析图片内容");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("无法解析图片内容，请换一张图片试试");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IllegalArgumentException(
                            "图片尺寸过大，最长边不能超过 " + MAX_DIMENSION + " 像素");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法解析图片内容，请换一张图片试试");
        }
    }

    /** 从存储的 URL 里取出文件名；不是本服务生成的 URL 一律返回 null */
    private String filenameOf(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(URL_PREFIX)) {
            return null;
        }
        String filename = avatarUrl.substring(URL_PREFIX.length());
        return SAFE_NAME.matcher(filename).matches() ? filename : null;
    }
}
