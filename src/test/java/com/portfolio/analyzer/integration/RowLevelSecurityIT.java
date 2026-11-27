package com.portfolio.analyzer.integration;

import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FORCE RLS for {@code app_rw}. The first three tests replace the
 * {@code @Disabled("waiting V3 RLS")} placeholders from the test-enhancement PR.
 */
class RowLevelSecurityIT extends AbstractPgVectorIT {

    private static final String INSERT_DOCUMENT =
            "INSERT INTO document (document_id, tenant_id, owner_user_id, embedding_model, embedding_dim) "
                    + "VALUES (?, ?, ?, 'text-embedding-v4', 1024)";

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private RagQueryService queryService;

    @Autowired
    private VectorStorePort vectorStore;

    @Test
    void applicationUserIsAppRw() {
        assertThat(jdbcTemplate.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_RW_USER);
    }

    @Test
    void tenantTableAccessOutsideATransactionFails() {
        assertThatThrownBy(() -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document", Integer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
        assertThatThrownBy(() -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingAppTenantIdReturnsNoRows() {
        insertChunk("tenant-rls-a", "visible-only-with-setting");
        Integer chunks = withoutTenant(() ->
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class));
        Integer documents = withoutTenant(() ->
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document", Integer.class));
        assertThat(chunks).isZero();
        assertThat(documents).isZero();
        assertThat(ownerJdbc().queryForObject("SELECT COUNT(*) FROM chunk", Integer.class)).isEqualTo(1);
    }

    @Test
    void setLocalTenantIdDoesNotLeakAcrossPooledTransactions() {
        for (int i = 0; i < 30; i++) {
            String tenant = i % 2 == 0 ? "tenant-rls-a" : "tenant-rls-b";
            withoutTenant(() -> {
                assertThat(currentSetting()).isNullOrEmpty();
                jdbcTemplate.queryForObject(
                        "SELECT set_config('app.tenant_id', ?, true)", String.class, tenant);
                insertChunkInCurrentTransaction(tenant, "local-" + tenant);
                assertThat(currentSetting()).isEqualTo(tenant);
                return null;
            });
        }
        assertThat(currentSetting()).isNullOrEmpty();
        Integer visible = withoutTenant(() ->
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class));
        assertThat(visible).isZero();
    }

    @Test
    void rowLevelSecurityIsolatesTenantsWithoutExplicitWhere() {
        inTenant("tenant-rls-a", () -> insertChunkInCurrentTransaction("tenant-rls-a", "CANARY-A"));
        inTenant("tenant-rls-b", () -> insertChunkInCurrentTransaction("tenant-rls-b", "CANARY-B"));

        inTenant("tenant-rls-a", () -> {
            List<String> contents = jdbcTemplate.queryForList("SELECT content FROM chunk", String.class);
            assertThat(contents).containsExactly("CANARY-A");
        });
        inTenant("tenant-rls-b", () -> {
            List<String> contents = jdbcTemplate.queryForList("SELECT content FROM chunk", String.class);
            assertThat(contents).containsExactly("CANARY-B");
        });
    }

    @Test
    void withCheckRejectsInsertForAnotherTenant() {
        assertThatThrownBy(() -> inTenant("tenant-a", () ->
                jdbcTemplate.update(INSERT_DOCUMENT, UUID.randomUUID(), "tenant-b", "user-b")))
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> assertThat(messages(error)).contains("row-level security"));

        inTenant("tenant-a", () -> insertChunkInCurrentTransaction("tenant-a", "owned-by-a"));
        UUID docId = ownerJdbc().queryForObject("SELECT document_id FROM document LIMIT 1", UUID.class);
        assertThatThrownBy(() -> inTenant("tenant-a", () -> jdbcTemplate.update(
                "INSERT INTO chunk (tenant_id, doc_id, chunk_index, source_type, owner_user_id, "
                        + "embedding_model, embedding_dim, content, embedding) "
                        + "VALUES ('tenant-b', ?, 0, 'text', 'user-b', 'text-embedding-v4', 1024, 'x', ?::vector)",
                docId, zeroVector(1024))))
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> assertThat(messages(error)).contains("row-level security"));

        assertThat(ownerJdbc().queryForObject(
                "SELECT COUNT(*) FROM document WHERE tenant_id = 'tenant-b'", Integer.class)).isZero();
        assertThat(ownerJdbc().queryForObject(
                "SELECT COUNT(*) FROM chunk WHERE tenant_id = 'tenant-b'", Integer.class)).isZero();
    }

    @Test
    void crossTenantForeignKeyStillRejectedUnderAppRw() {
        UUID docId = UUID.randomUUID();
        inTenant("tenant-a", () -> jdbcTemplate.update(INSERT_DOCUMENT, docId, "tenant-a", "user-a"));

        assertThatThrownBy(() -> inTenant("tenant-b", () -> jdbcTemplate.update(
                "INSERT INTO chunk (tenant_id, doc_id, chunk_index, source_type, owner_user_id, "
                        + "embedding_model, embedding_dim, content, embedding) "
                        + "VALUES ('tenant-b', ?, 0, 'text', 'user-b', 'text-embedding-v4', 1024, 'x', ?::vector)",
                docId, zeroVector(1024))))
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> assertThat(messages(error)).contains("fk_chunk_document"));
    }

    @Test
    void tenantACannotSearchTenantB() throws Exception {
        TenantPrincipal a = principal("tenant-a", "user-a", "group-a");
        TenantPrincipal b = principal("tenant-b", "user-b", "group-b");
        ingestionService.ingestDocument(a, textFile("secret-a.txt", "ALPHA confidential payload"), "src-a");
        ingestionService.ingestDocument(b, textFile("secret-b.txt", "BETA confidential payload"), "src-b");

        RagQueryResponse seenByA = queryService.queryWithContext(a, "confidential");
        assertThat(seenByA.getSources()).isNotEmpty();
        assertThat(seenByA.getSources()).allSatisfy(source -> {
            assertThat(source.getExcerpt()).contains("ALPHA");
            assertThat(source.getExcerpt()).doesNotContain("BETA");
        });

        RagQueryResponse seenByB = queryService.queryWithContext(b, "ALPHA");
        assertThat(seenByB.getSources()).isNotEmpty();
        assertThat(seenByB.getSources()).allSatisfy(source -> {
            assertThat(source.getExcerpt()).contains("BETA");
            assertThat(source.getExcerpt()).doesNotContain("ALPHA");
        });
    }

    @Test
    void sameGroupNameDoesNotCrossTenants() throws Exception {
        TenantPrincipal a = principal("tenant-a", "user-a", "g1");
        TenantPrincipal b = principal("tenant-b", "user-b", "g1");
        ingestionService.ingestDocument(a, textFile("a.txt", "ALPHA group document"), "src");
        ingestionService.ingestDocument(b, textFile("b.txt", "BETA group document"), "src");

        RagQueryResponse response = queryService.queryWithContext(a, "group document");
        assertThat(response.getSources()).isNotEmpty();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .allSatisfy(excerpt -> {
                    assertThat(excerpt).contains("ALPHA");
                    assertThat(excerpt).doesNotContain("BETA");
                });
    }

    @Test
    void tenantADeleteOrAclUpdateOfTenantBAffectsZeroRows() throws Exception {
        TenantPrincipal a = principal("tenant-a", "user-a", "g1");
        TenantPrincipal b = principal("tenant-b", "user-b", "g1");

        var own = ingestionService.ingestDocument(a, textFile("own.txt", "ALPHA own document"), "src");
        String ownId = String.valueOf(own.get("documentId"));
        var foreign = ingestionService.ingestDocument(b, textFile("foreign.txt", "BETA foreign document"), "src");
        UUID foreignId = UUID.fromString(String.valueOf(foreign.get("documentId")));

        assertThat(vectorStore.deleteChunksByDocument(a, foreignId)).isZero();
        vectorStore.updateDocumentAcl(a, foreignId, "user-a", Set.of("g1"));
        ingestionService.deleteDocument(a, foreignId.toString());

        assertThat(ownerJdbc().queryForObject(
                "SELECT COUNT(*) FROM document WHERE document_id = ?", Integer.class, foreignId)).isEqualTo(1);
        assertThat(ownerJdbc().queryForObject(
                "SELECT owner_user_id FROM document WHERE document_id = ?", String.class, foreignId))
                .isEqualTo("user-b");
        assertThat(ownerJdbc().queryForObject(
                "SELECT COUNT(*) FROM chunk WHERE doc_id = ?", Integer.class, foreignId)).isGreaterThan(0);
        assertThat(queryService.queryWithContext(b, "BETA").getSources()).isNotEmpty();

        ingestionService.deleteDocument(a, ownId);
        assertThat(ownerJdbc().queryForObject(
                "SELECT COUNT(*) FROM document WHERE document_id = ?::uuid",
                Integer.class, ownId)).isZero();
    }

    @Test
    void concurrentTenantsDoNotCrossOnThePool() throws Exception {
        inTenant("tenant-a", () -> jdbcTemplate.update(INSERT_DOCUMENT, UUID.randomUUID(), "tenant-a", "user-a"));
        inTenant("tenant-b", () -> jdbcTemplate.update(INSERT_DOCUMENT, UUID.randomUUID(), "tenant-b", "user-b"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?>[] tasks = new Future<?>[100];
            for (int i = 0; i < tasks.length; i++) {
                String tenant = i % 2 == 0 ? "tenant-a" : "tenant-b";
                tasks[i] = pool.submit(() -> inTenant(tenant, () -> {
                    assertThat(currentSetting()).isEqualTo(tenant);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document", Integer.class))
                            .isEqualTo(1);
                    assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM document WHERE tenant_id <> ?", Integer.class, tenant))
                            .isZero();
                }));
            }
            for (Future<?> task : tasks) {
                task.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void insertChunk(String tenantId, String content) {
        inTenant(tenantId, () -> insertChunkInCurrentTransaction(tenantId, content));
    }

    private void insertChunkInCurrentTransaction(String tenantId, String content) {
        UUID docId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document (document_id, tenant_id, owner_user_id, embedding_model, embedding_dim) "
                        + "VALUES (?, ?, 'user', 'text-embedding-v4', 1024)",
                docId, tenantId);
        jdbcTemplate.update(
                "INSERT INTO chunk (tenant_id, doc_id, chunk_index, source_type, owner_user_id, "
                        + "embedding_model, embedding_dim, content, embedding) "
                        + "VALUES (?, ?, 0, 'text', 'user', 'text-embedding-v4', 1024, ?, ?::vector)",
                tenantId, docId, content, zeroVector(1024));
    }

    private String currentSetting() {
        return jdbcTemplate.query(
                "SELECT current_setting('app.tenant_id', true)",
                rs -> rs.next() ? rs.getString(1) : null);
    }

    private static String messages(Throwable error) {
        StringBuilder all = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null) {
                all.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return all.toString();
    }

    private static TenantPrincipal principal(String tenantId, String userId, String group) {
        return new TenantPrincipal(tenantId, userId, Set.of(), Set.of(group));
    }

    private static MockMultipartFile textFile(String name, String body) {
        return new MockMultipartFile("file", name, "text/plain", body.getBytes());
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
