package com.portfolio.analyzer.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：验证Flyway迁移和数据库schema
 * 
 * @since Stage 2 Refactor
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class FlywayMigrationIT {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "false");
    }
    
    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Test
    void shouldCreateDocumentTable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'document'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }
    
    @Test
    void shouldCreateDocumentChunkTable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'document_chunk'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }
    
    @Test
    void shouldCreateVectorExtension() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                Boolean.class
        );
        assertThat(exists).isTrue();
    }
    
    @Test
    void shouldCreateHnswIndex() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_chunk_embedding'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }
    
    @Test
    void documentChunkTableShouldHave1024DimVector() {
        String dataType = jdbcTemplate.queryForObject(
                "SELECT udt_name FROM information_schema.columns " +
                "WHERE table_name = 'document_chunk' AND column_name = 'embedding'",
                String.class
        );
        assertThat(dataType).isEqualTo("vector");
    }
}
