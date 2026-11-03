package com.portfolio.analyzer.security;

import com.portfolio.analyzer.AnalyzerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DevProfileGuardTest {

    @Test
    void initializerIsRegisteredOnSpringApplication() {
        SpringApplication application = new SpringApplication(AnalyzerApplication.class);
        assertTrue(application.getInitializers().stream()
                .anyMatch(DevProfileGuardInitializer.class::isInstance));
    }

    @Test
    void devAndProdTogetherAreRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> initialize("jdbc:postgresql://localhost:5432/analyzer", "dev", "prod"));
        assertTrue(ex.getMessage().contains(DevProfileGuardInitializer.BOTH_PROFILES_MESSAGE));
    }

    @Test
    void localAndProductionTogetherAreRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> initialize("jdbc:h2:mem:devdb", "local", "production"));
        assertTrue(ex.getMessage().contains("prod/production"));
    }

    @Test
    void devWithRemoteDatasourceIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> initialize("jdbc:postgresql://db.internal:5432/analyzer", "dev"));
        assertTrue(ex.getMessage().contains("local datasource"));
        assertTrue(ex.getMessage().contains("db.internal"));
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void devWithUserInfoLocalhostIsAllowed() {
        initialize("jdbc:postgresql://analyzer:secret@localhost:5432/analyzer", "dev");
    }

    @Test
    void devWithEmbeddedH2IsAllowed() {
        initialize("jdbc:h2:mem:analyzer;MODE=PostgreSQL", "local");
    }

    @Test
    void prodWithRemoteDatasourceIsAllowedByTheGuard() {
        initialize("jdbc:postgresql://db.internal:5432/analyzer", "prod");
    }

    @Test
    void springApplicationRejectsDevPlusProdBeforeBeans() {
        assertStartupFails(
                new String[]{
                        "--spring.profiles.active=dev,prod",
                        "--spring.datasource.url=jdbc:postgresql://localhost:5432/analyzer",
                        "--spring.flyway.enabled=false",
                        "--spring.ai.openai.api-key=test-key"
                },
                "prod/production");
    }

    @Test
    void springApplicationRejectsDevWithNonLocalDatasource() {
        assertStartupFails(
                new String[]{
                        "--spring.profiles.active=dev",
                        "--spring.datasource.url=jdbc:postgresql://prod.db.example:5432/analyzer",
                        "--spring.flyway.enabled=false",
                        "--spring.ai.openai.api-key=test-key"
                },
                "local datasource");
    }

    @Test
    void prodProfileFailsFastWhenIssuerIsMissing() {
        assertStartupFails(
                new String[]{
                        "--spring.profiles.active=prod",
                        "--spring.flyway.enabled=false",
                        "--spring.datasource.url=jdbc:h2:mem:issuercheck;MODE=PostgreSQL",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=none",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.ai.openai.api-key=test-key",
                        "--spring.ai.openai.base-url=http://127.0.0.1:9",
                        "--app.security.jwt.jwk-set-uri=https://issuer.example/jwks",
                        "--app.security.jwt.audience=image-text-analyzer"
                },
                "JWT issuer");
    }

    @Test
    void parsesHostWithoutLeakingUserInfo() {
        assertTrue(DevProfileGuardInitializer.isLocalDatasource(
                "jdbc:postgresql://user:secret@127.0.0.1:5432/analyzer"));
        assertTrue(DevProfileGuardInitializer.isLocalDatasource(
                "jdbc:postgresql://[::1]:5432/analyzer"));
        assertFalse(DevProfileGuardInitializer.isLocalDatasource(
                "jdbc:postgresql://db.internal:5432/analyzer"));
        assertFalse(DevProfileGuardInitializer.isLocalDatasource(null));
        assertFalse(DevProfileGuardInitializer.isLocalDatasource("jdbc:h2:tcp://db.internal/mem:x"));
    }

    private static void initialize(String url, String... profiles) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test-datasource", Map.of("spring.datasource.url", url)));
            new DevProfileGuardInitializer().initialize(context);
        }
    }

    private static void assertStartupFails(String[] args, String messagePart) {
        SpringApplication application = new SpringApplication(AnalyzerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        try {
            var context = application.run(args);
            context.close();
            fail("expected startup to fail");
        } catch (Throwable ex) {
            StringBuilder messages = new StringBuilder();
            Throwable current = ex;
            while (current != null && messages.length() < 12000) {
                if (current.getMessage() != null) {
                    messages.append(current.getMessage()).append('\n');
                }
                current = current.getCause();
            }
            assertTrue(messages.toString().contains(messagePart), messages.toString());
        }
    }
}
