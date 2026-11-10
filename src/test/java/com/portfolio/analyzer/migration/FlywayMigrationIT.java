package com.portfolio.analyzer.migration;

import com.portfolio.analyzer.testsupport.AppRwIntegrationSupport;
import org.flywaydb.core.Flyway;
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
        AppRwIntegrationSupport.register(registry, postgres);
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

    @Test
    void applicationDatasourceIsAppRw() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_user")) {
            assertTrue(rs.next());
            assertEquals(AppRwIntegrationSupport.APP_RW_USER, rs.getString(1));
        }
    }

    @Test
    void v3EnablesForcedRlsAndNonBypassRole() {
        JdbcTemplate app = new JdbcTemplate(dataSource);
        JdbcTemplate owner = AppRwIntegrationSupport.ownerJdbc(postgres);

        Integer v3 = owner.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success",
                Integer.class);
        assertEquals(1, v3);

        var tables = app.queryForList(
                "SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity, "
                        + "pg_get_userbyid(c.relowner) AS owner "
                        + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname IN ('document', 'chunk') "
                        + "ORDER BY c.relname");
        assertEquals(2, tables.size());
        for (var row : tables) {
            assertEquals(Boolean.TRUE, row.get("relrowsecurity"), row.get("relname").toString());
            assertEquals(Boolean.TRUE, row.get("relforcerowsecurity"), row.get("relname").toString());
            assertNotEquals("app_rw", row.get("owner"));
        }

        var policies = app.queryForList(
                "SELECT tablename, policyname, qual, with_check FROM pg_policies "
                        + "WHERE schemaname = 'public' AND tablename IN ('document', 'chunk')");
        assertEquals(2, policies.size());
        for (var row : policies) {
            String qual = String.valueOf(row.get("qual"));
            String check = String.valueOf(row.get("with_check"));
            assertTrue(qual.contains("app.tenant_id"), qual);
            assertTrue(check.contains("app.tenant_id"), check);
        }

        var role = app.queryForMap(
                "SELECT rolbypassrls, rolsuper FROM pg_roles WHERE rolname = 'app_rw'");
        assertEquals(Boolean.FALSE, role.get("rolbypassrls"));
        assertEquals(Boolean.FALSE, role.get("rolsuper"));
    }
}
