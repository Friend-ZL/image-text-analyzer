package com.portfolio.analyzer.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用真实应用上下文证明 dev 租户实现不会在 prod 或默认 profile 下装载。
 */
class DevTenantContextActivationTest {

    @Nested
    @ActiveProfiles("prod")
    class ProdProfile extends LoadedContext {

        @Test
        void doesNotLoadDevTenantContext() {
            assertJwtTenantOnly();
        }
    }

    @Nested
    class DefaultProfile extends LoadedContext {

        @Test
        void doesNotLoadDevTenantContext() {
            assertJwtTenantOnly();
        }
    }

    @Nested
    @ActiveProfiles("dev")
    class DevProfile extends LoadedContext {

        @Test
        void loadsDevTenantContext() {
            assertThat(applicationContext.getBeansOfType(JwtTenantContext.class)).isEmpty();
            assertThat(applicationContext.getBean(TenantContext.class)).isInstanceOf(DevTenantContext.class);
        }
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:h2:mem:tenantprofile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "app.security.jwt.issuer=https://issuer.example/realms/app",
            "app.security.jwt.jwk-set-uri=https://issuer.example/protocol/openid-connect/certs",
            "app.security.jwt.audience=image-text-analyzer",
            "spring.ai.openai.api-key=test-key",
            "spring.ai.openai.base-url=http://127.0.0.1:9"
    })
    abstract static class LoadedContext {

        @Autowired
        ApplicationContext applicationContext;

        void assertJwtTenantOnly() {
            assertThat(applicationContext.getBeansOfType(DevTenantContext.class)).isEmpty();
            assertThat(applicationContext.getBean(TenantContext.class)).isInstanceOf(JwtTenantContext.class);
        }
    }
}
