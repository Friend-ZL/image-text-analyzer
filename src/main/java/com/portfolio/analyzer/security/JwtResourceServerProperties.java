package com.portfolio.analyzer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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

    /**
     * Keycloak 客户端 ID。角色还从 {@code resource_access.<client-id>.roles} 读取。
     * 对应 JWT_CLIENT_ID。不能含点，点分路径会把它拆开。
     */
    private String clientId = "image-text-analyzer";

    /**
     * 令牌时间校验的时钟偏差。对应 JWT_CLOCK_SKEW_SECONDS，默认 60 秒。
     */
    private Duration clockSkew = Duration.ofSeconds(60);

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("app.security.jwt.client-id must not be blank");
        }
        if (clientId.indexOf('.') >= 0) {
            throw new IllegalStateException(
                    "app.security.jwt.client-id must be a single path segment without '.'");
        }
        this.clientId = clientId.trim();
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(String raw) {
        this.clockSkew = parseClockSkew(raw);
    }

    static Duration parseClockSkew(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("app.security.jwt.clock-skew must not be blank");
        }
        String trimmed = raw.trim();
        Duration parsed;
        if (trimmed.matches("-?\\d+")) {
            parsed = Duration.ofSeconds(Long.parseLong(trimmed));
        } else {
            parsed = DurationStyle.detectAndParse(trimmed);
        }
        if (parsed.isNegative()) {
            throw new IllegalStateException("app.security.jwt.clock-skew must not be negative");
        }
        return parsed;
    }
}
