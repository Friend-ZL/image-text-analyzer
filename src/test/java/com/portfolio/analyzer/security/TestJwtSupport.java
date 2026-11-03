package com.portfolio.analyzer.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;

/**
 * 测试用 RSA 密钥。MockMvc 的 jwt() 不经过 decoder；
 * TestRestTemplate 需要一把能被测试 decoder 验签的 token。
 */
public final class TestJwtSupport {

    private static final RSAKey RSA_KEY = generate();

    private TestJwtSupport() {
    }

    public static JwtDecoder decoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey()).build();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to build test JwtDecoder", ex);
        }
    }

    public static String bearerToken() {
        Instant now = Instant.now();
        return sign(new JWTClaimsSet.Builder()
                .subject("user1")
                .claim("tenant_id", "tenant1")
                .issueTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .build());
    }

    private static String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(RSA_KEY));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign test JWT", ex);
        }
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
