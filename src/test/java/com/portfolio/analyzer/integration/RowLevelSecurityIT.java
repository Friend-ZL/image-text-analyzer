package com.portfolio.analyzer.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Placeholders for database-enforced tenant isolation.
 *
 * <p>V1/V2 have no RLS policies. {@code JdbcVectorStorePort.setTenantContext}
 * does not call {@code set_config('app.tenant_id', ..., true)} yet. Enable this
 * class when BE-3's V3 migration adds FORCE ROW LEVEL SECURITY for {@code app_rw}
 * with {@code USING (tenant_id = current_setting('app.tenant_id', true))}.
 * {@code app_rw} is already a non-owner, non-superuser, {@code NOBYPASSRLS} role.
 */
@Disabled("waiting V3 RLS")
class RowLevelSecurityIT extends AbstractPgVectorIT {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void missingAppTenantIdReturnsNoRows() {
        insertChunk("tenant-rls-a", "visible-only-with-setting");
        Integer visible = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class);
        assertThat(visible).isZero();
    }

    @Test
    void setLocalTenantIdDoesNotLeakAcrossPooledTransactions() {
        inTransaction("tenant-rls-a", () -> insertChunkInCurrentTransaction("tenant-rls-a", "local-a"));
        String setting = jdbcTemplate.queryForObject(
                "SELECT current_setting('app.tenant_id', true)",
                String.class
        );
        assertThat(setting == null || setting.isBlank()).isTrue();
        Integer visible = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class);
        assertThat(visible).isZero();
    }

    @Test
    void rowLevelSecurityIsolatesTenantsWithoutExplicitWhere() {
        inTransaction("tenant-rls-a", () -> insertChunkInCurrentTransaction("tenant-rls-a", "CANARY-A"));
        inTransaction("tenant-rls-b", () -> insertChunkInCurrentTransaction("tenant-rls-b", "CANARY-B"));

        inTransaction("tenant-rls-a", () -> {
            List<String> contents = jdbcTemplate.queryForList("SELECT content FROM chunk", String.class);
            assertThat(contents).containsExactly("CANARY-A");
        });
        inTransaction("tenant-rls-b", () -> {
            List<String> contents = jdbcTemplate.queryForList("SELECT content FROM chunk", String.class);
            assertThat(contents).containsExactly("CANARY-B");
        });
    }

    private void insertChunk(String tenantId, String content) {
        inTransaction(tenantId, () -> insertChunkInCurrentTransaction(tenantId, content));
    }

    private void insertChunkInCurrentTransaction(String tenantId, String content) {
        UUID docId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document (document_id, tenant_id, owner_user_id, embedding_model, embedding_dim) " +
                        "VALUES (?, ?, 'user', 'text-embedding-v4', 1024)",
                docId, tenantId
        );
        jdbcTemplate.update(
                "INSERT INTO chunk (tenant_id, doc_id, chunk_index, source_type, owner_user_id, " +
                        "embedding_model, embedding_dim, content, embedding) " +
                        "VALUES (?, ?, 0, 'text', 'user', 'text-embedding-v4', 1024, ?, ?::vector)",
                tenantId, docId, content, zeroVector(1024)
        );
    }

    private void inTransaction(String tenantId, Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class,
                    tenantId
            );
            work.run();
        });
    }

    private static String zeroVector(int dimensions) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < dimensions; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('0');
        }
        return builder.append(']').toString();
    }
}
