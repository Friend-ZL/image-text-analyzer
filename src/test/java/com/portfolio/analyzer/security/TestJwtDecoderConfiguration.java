package com.portfolio.analyzer.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 测试 profile 的 JwtDecoder。生产 decoder 在 test profile 下不注册，
 * 因此空的 JWT_JWK_SET_URI 不会在测试里触发 jwkSetUri cannot be empty。
 */
@Configuration
@Profile("test")
public class TestJwtDecoderConfiguration {

    @Bean
    public JwtDecoder jwtDecoder() {
        return TestJwtSupport.decoder();
    }
}
