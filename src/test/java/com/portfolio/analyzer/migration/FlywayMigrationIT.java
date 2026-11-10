package com.portfolio.analyzer.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flyway 迁移集成测试
 * <p>
 * 使用 Testcontainers pgvector/pgvector:pg16 镜像测试 Flyway 迁移。
 * CI 环境（CI=true）必须有 Docker，否则测试失败。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class FlywayMigrationIT {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
    }
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private Flyway flyway;
    
    @Test
    void shouldApplyBaselineMigration() {
        assertNotNull(flyway);
        
        var info = flyway.info();
        var migrations = info.all();
        
        assertTrue(migrations.length > 0, "Should have at least one migration");
        assertEquals("1", migrations[0].getVersion().getVersion(), 
            "First migration should be V1");
        assertTrue(migrations[0].getDescription().contains("baseline"),
            "First migration should be baseline");
    }
    
    @Test
    void shouldEnableVectorExtension() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT extname FROM pg_extension WHERE extname = 'vector'"
             )) {
            
            assertTrue(rs.next(), "Vector extension should be installed");
            assertEquals("vector", rs.getString("extname"));
        }
    }
    
    @Test
    void shouldHaveCorrectBaselineComment() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT obj_description(oid, 'pg_extension') as comment " +
                     "FROM pg_extension WHERE extname = 'vector'"
             )) {
            
            assertTrue(rs.next());
            String comment = rs.getString("comment");
            assertNotNull(comment);
            assertTrue(comment.contains("RAG"), 
                "Extension comment should mention RAG");
        }
    }
    
    /**
     * V1 只记录基线迁移本身。后续 PR（#2 起）可以追加 V2+ 表，本测试不禁止那些表存在。
     */
    @Test
    void baselineHistoryRecordsSuccessfulV1() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT version, description, success FROM flyway_schema_history WHERE version = '1'"
             )) {

            assertTrue(rs.next(), "V1 baseline must be recorded in flyway_schema_history");
            assertEquals("1", rs.getString("version"));
            assertTrue(rs.getString("description").toLowerCase().contains("baseline"),
                    "V1 description should identify the baseline migration");
            assertTrue(rs.getBoolean("success"));
            assertFalse(rs.next(), "version 1 should be a single history row");
        }
    }
    
    @Test
    void shouldCompleteSuccessfully() {
        var result = flyway.info();
        
        assertNotNull(result);
        assertEquals(0, result.pending().length, 
            "All migrations should be applied");
    }
}
