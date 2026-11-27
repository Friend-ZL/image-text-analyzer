package com.portfolio.analyzer.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * One pgvector container and one Spring context for every IT.
 *
 * <p>Flyway connects as the container owner and V3 creates {@code app_rw}.
 * The application datasource connects as that role. The password is a test
 * value passed through the Flyway placeholder {@code appRwPassword}; it is
 * not a production secret. Subclasses must not add {@code @SpringBootTest},
 * {@code @ActiveProfiles}, {@code @Import}, {@code @MockBean}, or
 * {@code @DynamicPropertySource}; any of those would split the cached context.
 *
 * <p>{@code app_rw} cannot {@code TRUNCATE} (that bypasses RLS). Cleanup uses
 * the owner connection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ItStubsConfig.class)
public abstract class AbstractPgVectorIT {

    protected static final String APP_RW_USER = "app_rw";
    protected static final String APP_RW_PASSWORD = "test-app-rw-password";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private static ApplicationContext sharedContext;

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_RW_USER);
        registry.add("spring.datasource.password", () -> APP_RW_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.appRwPassword", () -> APP_RW_PASSWORD);
        registry.add("DATABASE_APP_RW_PASSWORD", () -> APP_RW_PASSWORD);
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        truncateAsOwner();
        CapturedChatPrompt.clear();
        HnswIterativeScanProbe.reset();
    }

    protected void inTenant(String tenantId, Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId);
            work.run();
        });
    }

    protected <T> T inTenant(String tenantId, Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId);
            return work.get();
        });
    }

    protected <T> T withoutTenant(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    protected JdbcTemplate ownerJdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }

    private void truncateAsOwner() {
        try (Connection connection = ownerJdbc().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE chunk, document RESTART IDENTITY CASCADE");
        } catch (SQLException ex) {
            throw new IllegalStateException("owner truncate of tenant tables failed", ex);
        }
    }
}
