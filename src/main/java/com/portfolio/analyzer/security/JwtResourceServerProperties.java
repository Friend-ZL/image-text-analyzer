package com.portfolio.analyzer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 生产环境 JWT 资源服务器设置。
 * <p>
 * 不写入 {@code spring.security.oauth2.resourceserver.jwt.*}：空字符串也会被
 * Boot 当成“已配置”，从而创建不校验 iss 的 decoder，或在启动时因空 jwkSetUri 失败。
 */
@Configuration
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtResourceServerProperties {

    /**
     * 期望的 iss。对应环境变量 JWT_ISSUER_URI。
     */
    private String issuer = "";

    /**
     * JWK Set 地址。对应环境变量 JWT_JWK_SET_URI。
     */
    private String jwkSetUri = "";

    /**
     * 期望的受众。匹配 JWT 的 aud，或在 aud 缺失时匹配 azp。对应 JWT_AUDIENCE。
     */
    private String audience = "";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
