package com.smartstorm.config;

import com.smartstorm.fisco.FiscoConfig;
import com.smartstorm.service.ChainClient;
import com.smartstorm.service.NoopChainClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存证链条件化装配的测试。
 *
 * <p>这几条测试守的是本方案里风险最高的一个假设：<b>FISCO SDK 的原生库在 Windows 上
 * 加载不了，所以链未启用时那个类必须永远不被加载</b>。它同时是条件装配的功能测试，
 * 也是原生库可用性的探针。</p>
 */
class AnchorConfigConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NoopChainConfig.class, FiscoConfig.class);

    @Test
    @DisplayName("未配置时默认装配空实现：链关闭，且完全不触碰 FISCO SDK")
    void disabledByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ChainClient.class);
            ChainClient client = ctx.getBean(ChainClient.class);
            assertThat(client).isInstanceOf(NoopChainClient.class);
            assertThat(client.isEnabled()).isFalse();
        });
    }

    @Test
    @DisplayName("显式 enabled=false 时同样是空实现")
    void explicitlyDisabled() {
        runner.withPropertyValues("app.fisco.enabled=false").run(ctx -> {
            assertThat(ctx.getBean(ChainClient.class)).isInstanceOf(NoopChainClient.class);
        });
    }

    @Test
    @DisplayName("enabled=true 但合约地址为空：启动即失败，而不是拖到第一次锚定才报错")
    void enabledWithoutContractAddressFailsFast() {
        runner.withPropertyValues("app.fisco.enabled=true", "app.fisco.contract-address=")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(causeChain(ctx.getStartupFailure()))
                            .contains("contract-address");
                });
    }

    /**
     * 原生库探针。
     *
     * <p>{@code enabled=true} 会真正触发 {@code FiscoChainClient} 的构造，进而加载
     * FISCO SDK 的类、尝试装载 JNI 原生库。这里故意给一个不存在的配置文件，预期失败原因
     * 是"读不到配置文件"，而<b>不是</b> {@code UnsatisfiedLinkError}。</p>
     *
     * <p>若这条测试因 {@code UnsatisfiedLinkError} 失败，结论就是：<b>Windows 上跑不了
     * FISCO SDK，后端必须整体放进虚拟机</b>（前端仍可留在宿主机）。那就是需要知道的答案。</p>
     */
    @Test
    @DisplayName("原生库探针：enabled=true 时应因配置文件缺失而失败，而非原生库加载失败")
    void nativeLibraryProbe() {
        runner.withPropertyValues(
                        "app.fisco.enabled=true",
                        "app.fisco.contract-address=0x0000000000000000000000000000000000000000",
                        "app.fisco.config-file=definitely-not-here.toml")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    String chain = causeChain(ctx.getStartupFailure());
                    assertThat(chain)
                            .as("FISCO SDK 的类应能正常加载。出现 UnsatisfiedLinkError 说明 "
                                    + "Windows 缺少 JNI 原生库，后端需搬进 Linux 虚拟机运行。"
                                    + "实际异常链：%s", chain)
                            .doesNotContain("UnsatisfiedLinkError")
                            .doesNotContain("NoClassDefFoundError");
                });
    }

    /** 把异常因果链拼成字符串，便于断言与排查 */
    private static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 15) {
            sb.append(cur.getClass().getName()).append(": ").append(cur.getMessage()).append('\n');
            cur = cur.getCause();
        }
        return sb.toString();
    }
}
