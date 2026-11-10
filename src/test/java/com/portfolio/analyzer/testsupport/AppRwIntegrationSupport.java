package com.portfolio.analyzer.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * IT 里 Flyway 用容器 owner，应用数据源用 V3 创建的 app_rw。
 * 不是测试基类。各 IT 仍自己声明容器。
 */
public final class AppRwIntegrationSupport {

    public static final String APP_RW_USER = "app_rw";
    public static final String APP_RW_PASSWORD = "test-app-rw-password";

    private AppRwIntegrationSupport() {
    }

    public static void register(DynamicPropertyRegistry registry, PostgreSQLContainer<?> postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_RW_USER);
        registry.add("spring.datasource.password", () -> APP_RW_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.appRwPassword", () -> APP_RW_PASSWORD);
        registry.add("DATABASE_APP_RW_PASSWORD", () -> APP_RW_PASSWORD);
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    public static JdbcTemplate ownerJdbc(PostgreSQLContainer<?> postgres) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }
}
