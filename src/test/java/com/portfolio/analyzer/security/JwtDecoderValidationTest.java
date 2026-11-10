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
        String jwkSetUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
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
    void failsFastWhenAudienceMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SecurityConfig.buildJwtDecoder("https://issuer.example/jwks", ISSUER, ""));
        assertTrue(ex.getMessage().contains("JWT audience"));
    }

    private String sign(String issuer, String audience, String azp) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("user-1")
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(300)));
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
