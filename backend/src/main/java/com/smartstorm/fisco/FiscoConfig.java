package com.smartstorm.fisco;

import com.smartstorm.service.ChainClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FISCO BCOS 存证链的装配（仅在 {@code app.fisco.enabled=true} 时生效）。
 *
 * <p>与 {@code NoopChainConfig} 用一对对称的 {@code @ConditionalOnProperty} 互斥，
 * 而不是 {@code @ConditionalOnMissingBean} —— 后者依赖 bean 定义的注册顺序，条件评估
 * 时机容易出意外。</p>
 *
 * <p><b>为什么 {@code @Bean} 方法返回的是 {@code ChainClient} 接口而不是
 * {@code FiscoChainClient}：</b>Spring 在加载 {@code @Configuration} 类时需要解析
 * 方法签名，若返回类型写的是 FISCO 类，就会触发该类的加载 —— 而它是 JNI 封装的原生库，
 * 在缺少原生库的平台上会直接加载失败，导致整个应用起不来。返回接口类型后，FISCO 类只在
 * 方法体<b>执行</b>时才被解析，本配置被条件排除时它永远不会被加载。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.fisco", name = "enabled", havingValue = "true")
public class FiscoConfig {

    @Bean
    public ChainClient chainClient(
            @Value("${app.fisco.config-file}") String configFile,
            @Value("${app.fisco.contract-address}") String contractAddress,
            @Value("${app.fisco.group:group0}") String group,
            @Value("${app.fisco.abi-location:abi/SmartStormAnchor.abi}") String abiLocation) {
        // 早失败：开着开关却没填合约地址，与其等到第一次锚定时才报错，不如启动就说清楚
        if (contractAddress == null || contractAddress.isBlank()) {
            throw new IllegalStateException("app.fisco.enabled=true 但 app.fisco.contract-address 为空；"
                    + "请先在控制台执行 deploy SmartStormAnchor，把得到的合约地址填进去");
        }
        return new FiscoChainClient(configFile, contractAddress, group, abiLocation);
    }
}
