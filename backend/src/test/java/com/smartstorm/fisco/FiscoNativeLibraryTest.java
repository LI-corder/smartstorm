package com.smartstorm.fisco;

import org.fisco.bcos.sdk.jni.common.JniLibLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * FISCO SDK 原生库的可用性探针。
 *
 * <p>这是本方案里风险最高的一个假设的守门测试。FISCO BCOS 的 Java SDK 底层是 JNI 封装的
 * C SDK，如果当前平台上没有可用的原生库，<b>整个后端都起不来</b>（不是存证功能不可用，
 * 是应用启动失败）。</p>
 *
 * <p>好消息是 {@code bcos-sdk-jni} 这个依赖把全平台的原生库都打包进去了：
 * {@code bcos-sdk-jni.dll}（Windows，含 MSVC 运行时）、{@code libbcos-sdk-jni.so}
 * （Linux x86_64 / aarch64）、{@code libbcos-sdk-jni.dylib}（macOS x86_64 / aarch64）。
 * 所以三大平台理论上都能直接跑，不需要把后端搬进虚拟机。</p>
 *
 * <p>这条测试把这个结论固化下来：一旦将来升级 SDK 后原生库不再可用，这里会立刻红。</p>
 */
class FiscoNativeLibraryTest {

    @Test
    @DisplayName("FISCO SDK 的 JNI 原生库能在本机加载")
    void nativeLibraryLoads() {
        assertDoesNotThrow(JniLibLoader::loadJniLibrary,
                () -> "FISCO SDK 原生库加载失败（当前 OS=" + JniLibLoader.getOs()
                        + ", ARCH=" + JniLibLoader.getArch() + "）。"
                        + "若该平台不在支持列表内，需把后端整体放进 Linux 虚拟机运行，"
                        + "前端可以继续留在宿主机。");
    }

    @Test
    @DisplayName("能识别出当前平台与对应的库名")
    void platformDetected() {
        String os = JniLibLoader.getOs();
        String arch = JniLibLoader.getArch();
        String libName = JniLibLoader.getLibName(os);
        System.out.println("FISCO 原生库平台信息：os=" + os + ", arch=" + arch + ", libName=" + libName);
        org.junit.jupiter.api.Assertions.assertNotNull(os);
        org.junit.jupiter.api.Assertions.assertNotNull(arch);
        org.junit.jupiter.api.Assertions.assertNotNull(libName);
    }
}
