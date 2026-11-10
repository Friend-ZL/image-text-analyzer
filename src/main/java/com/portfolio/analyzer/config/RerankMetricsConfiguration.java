package com.portfolio.analyzer.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 没有 actuator 时仍提供一个进程内的 MeterRegistry，让降级计数有地方写。
 * 引入 actuator 后由 Boot 的 registry 接管。
 */
@Configuration
public class RerankMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
