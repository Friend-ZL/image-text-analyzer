package com.portfolio.analyzer.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtDecoderValidationTest {

    private static final String ISSUER = "https://issuer.example/realms/app";
    private static final String AUDIENCE = "image-text-analyzer";

    private HttpServer server;
    private RSAKey rsaKey;
    private JwtDecoder decoder;
    private String jwkSetUri;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("prod-key").generate();
        byte[] jwks = new JWKSet(rsaKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        server.start();
        jwkSetUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
        decoder = SecurityConfig.buildJwtDecoder(jwkSetUri, ISSUER, AUDIENCE);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsTokenWithMatchingAudience() throws Exception {
        Jwt jwt = decoder.decode(sign(ISSUER, AUDIENCE, null));
        assertEquals("user-1", jwt.getSubject());
        assertTrue(jwt.getAudience().contains(AUDIENCE));
    }

    @Test
    void acceptsAzpWhenAudienceClaimIsAbsent() throws Exception {
        Jwt jwt = decoder.decode(sign(ISSUER, null, AUDIENCE));
        assertEquals(AUDIENCE, jwt.getClaimAsString("azp"));
    }

    @Test
    void rejectsWrongIssuer() {
        assertThrows(JwtException.class, () -> decoder.decode(sign("https://evil.example", AUDIENCE, null)));
    }

    @Test
    void rejectsWrongAudienceAndAzp() {
        assertThrows(JwtException.class, () -> decoder.decode(sign(ISSUER, "other-client", "other-client")));
    }

    @Test
    void failsFastWhenIssuerMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SecurityConfig.buildJwtDecoder("https://issuer.example/jwks", " ", AUDIENCE));
        assertTrue(ex.getMessage().contains("JWT issuer"));
    }

    @Test
    void failsFastWhenJwkSetUriMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SecurityConfig.buildJwtDecoder("", ISSUER, AUDIENCE));
        assertTrue(ex.getMessage().contains("JWK set URI"));
    }

    @Test
    void rejectsTokenExpiredBeyondDefaultClockSkew() throws Exception {
        Instant now = Instant.now();
        assertThrows(JwtException.class, () -> decoder.decode(
                signAt(ISSUER, AUDIENCE, null, now.minusSeconds(300), now.minusSeconds(120), null)));
    }

    @Test
    void acceptsTokenExpiredWithinDefaultClockSkew() throws Exception {
        Instant now = Instant.now();
        decoder.decode(signAt(ISSUER, AUDIENCE, null, now.minusSeconds(120), now.minusSeconds(30), null));
    }

    @Test
    void rejectsNotBeforeBeyondDefaultClockSkew() throws Exception {
        Instant now = Instant.now();
        assertThrows(JwtException.class, () -> decoder.decode(
                signAt(ISSUER, AUDIENCE, null, now, now.plusSeconds(300), now.plusSeconds(120))));
    }

    @Test
    void acceptsNotBeforeWithinDefaultClockSkew() throws Exception {
        Instant now = Instant.now();
        decoder.decode(signAt(ISSUER, AUDIENCE, null, now, now.plusSeconds(300), now.plusSeconds(30)));
    }

    @Test
    void configuredClockSkewOverridesTheDefault() throws Exception {
        JwtDecoder strict = SecurityConfig.buildJwtDecoder(jwkSetUri, ISSUER, AUDIENCE, Duration.ZERO);
        Instant now = Instant.now();
        String recentlyExpired = signAt(ISSUER, AUDIENCE, null, now.minusSeconds(30), now.minusSeconds(5), null);
        assertThrows(JwtException.class, () -> strict.decode(recentlyExpired));
        decoder.decode(recentlyExpired);
    }

    @Test
    void negativeClockSkewFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                SecurityConfig.buildJwtDecoder(jwkSetUri, ISSUER, AUDIENCE, Duration.ofSeconds(-1)));
        assertTrue(ex.getMessage().contains("clock-skew"));
    }

    @Test
    void failsFastWhenAudienceMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SecurityConfig.buildJwtDecoder("https://issuer.example/jwks", ISSUER, ""));
        assertTrue(ex.getMessage().contains("JWT audience"));
    }

    private String sign(String issuer, String audience, String azp) throws Exception {
        Instant now = Instant.now();
        return signAt(issuer, audience, azp, now.minusSeconds(30), now.plusSeconds(300), null);
    }

    private String signAt(String issuer, String audience, String azp,
                          Instant issuedAt, Instant expiresAt, Instant notBefore) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("user-1")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
        if (notBefore != null) {
            claims.notBeforeTime(Date.from(notBefore));
        }
        if (audience != null) {
            claims.audience(audience);
        }
        if (azp != null) {
            claims.claim("azp", azp);
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
