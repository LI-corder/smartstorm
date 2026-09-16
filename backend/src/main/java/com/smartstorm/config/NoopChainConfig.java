package com.smartstorm.config;

import com.smartstorm.service.ChainClient;
import com.smartstorm.service.NoopChainClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存证链未启用时的装配（默认）。
 *
 * <p>与 {@code FiscoConfig} 用一对对称的 {@code @ConditionalOnProperty} 互斥，而不是用
 * {@code @ConditionalOnMissingBean} —— 后者依赖 bean 定义的注册顺序，条件评估时机容易
 * 出意外；两个显式互斥的条件语义直白，行为可预测。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.fisco", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopChainConfig {

    @Bean
    public ChainClient chainClient() {
        return new NoopChainClient();
    }
}
