package com.portfolio.analyzer.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * 受众校验：配置的 audience 出现在 {@code aud} 中即通过；
 * 没有匹配的 aud 时，再接受相等的 {@code azp}（Keycloak 客户端 ID 常放在这里）。
 */
final class AudienceOrAzpValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    AudienceOrAzpValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (audience == null || audience.isBlank()) {
            return failure("JWT audience is not configured");
        }
        List<String> tokenAudience = token.getAudience();
        if (tokenAudience != null && tokenAudience.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        if (azpMatches(token.getClaim("azp"))) {
            return OAuth2TokenValidatorResult.success();
        }
        return failure("Token aud/azp does not match the required audience");
    }

    private boolean azpMatches(Object azp) {
        if (azp instanceof String value) {
            return audience.equals(value);
        }
        if (azp instanceof Collection<?> values) {
            for (Object value : values) {
                if (audience.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
