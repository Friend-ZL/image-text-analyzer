package com.portfolio.analyzer.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * 如果 Docker 不可用，测试会被跳过（见 @Testcontainers 注解）。
 */
@SpringBootTest
@Testcontainers
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
        registry.add("spring.flyway.enabled", () -> "true");
        
        // 禁用 JWT 配置
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "");
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
    
    @Test
    void shouldNotCreateChunkTable() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tablename FROM pg_tables " +
                     "WHERE tablename LIKE '%chunk%'"
             )) {
            
            assertFalse(rs.next(), 
                "V1 baseline should NOT create chunk table (reserved for PR #2)");
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
