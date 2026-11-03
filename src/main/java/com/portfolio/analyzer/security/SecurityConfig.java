package com.portfolio.analyzer.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置。
 * <p>
 * dev/local 以外始终安装 JWT 资源服务器链，不回退到 Boot 的 form/basic。
 * issuer、JWK、audience 任一为空都在创建 decoder 时失败，避免带着空 jwkSetUri 启动。
 * 测试 profile 不创建这个 decoder，由测试 classpath 上的 JwtDecoder 代替。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(CustomAuthenticationEntryPoint authenticationEntryPoint,
                          CustomAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    @Profile("!dev & !local")
    public SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http, TenantContext tenantContext) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/", "/index.html", "/static/**", "/favicon.ico").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .requestMatchers("/api/analyze").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .addFilterAfter(
                new TenantEnforcementFilter(tenantContext, authenticationEntryPoint),
                BearerTokenAuthenticationFilter.class
            );
        return http.build();
    }

    /**
     * 仅 dev/local，且不能与 prod 同时生效（启动守卫会先拒绝这种组合）。
     */
    @Bean
    @Profile("(dev | local) & !prod & !production")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }

    @Bean
    @Profile("!dev & !local & !test")
    public JwtDecoder jwtDecoder(JwtResourceServerProperties properties) {
        return buildJwtDecoder(
                properties.getJwkSetUri(),
                properties.getIssuer(),
                properties.getAudience()
        );
    }

    static JwtDecoder buildJwtDecoder(String jwkSetUri, String issuer, String audience) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException(
                    "JWT issuer is required outside dev/local profiles. Set JWT_ISSUER_URI.");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalStateException(
                    "JWT JWK set URI is required outside dev/local profiles. Set JWT_JWK_SET_URI.");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException(
                    "JWT audience is required outside dev/local profiles. Set JWT_AUDIENCE.");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceOrAzpValidator(audience)
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
