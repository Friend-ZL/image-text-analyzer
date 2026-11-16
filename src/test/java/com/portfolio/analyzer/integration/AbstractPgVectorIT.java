package com.portfolio.analyzer.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One pgvector container and one Spring context for every IT.
 *
 * <p>Flyway connects as the container owner (a superuser). The application
 * datasource connects as {@code app_rw}. Subclasses must not add
 * {@code @SpringBootTest}, {@code @ActiveProfiles}, {@code @Import},
 * {@code @MockBean}, or {@code @DynamicPropertySource}; any of those would
 * split the cached context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ItStubsConfig.class)
public abstract class AbstractPgVectorIT {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    /**
     * Generated at class-init time. Not a checked-in secret and not read from
     * {@code DATABASE_APP_RW_PASSWORD}.
     */
    protected static final String APP_RW_PASSWORD = AppRwRoleBootstrap.generatePassword();

    private static ApplicationContext sharedContext;

    static {
        POSTGRES.start();
        // Role creation and the post-Flyway GRANTs live only in AppRwRoleBootstrap.
        // BE-3: delete that class (and this call) when V3 creates app_rw.
        AppRwRoleBootstrap.ensureRole(POSTGRES, APP_RW_PASSWORD);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> AppRwRoleBootstrap.ROLE);
        registry.add("spring.datasource.password", () -> APP_RW_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void resetSharedDatabase() {
        if (sharedContext == null) {
            sharedContext = applicationContext;
        } else if (sharedContext != applicationContext) {
            throw new IllegalStateException(
                    "IT classes started more than one Spring context; keep their annotations identical");
        }
        jdbcTemplate.execute("TRUNCATE TABLE chunk, document RESTART IDENTITY CASCADE");
        CapturedChatPrompt.clear();
        HnswIterativeScanProbe.reset();
    }
}
