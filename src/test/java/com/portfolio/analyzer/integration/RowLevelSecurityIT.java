package com.portfolio.analyzer.integration;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.security.TenantContext;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import com.portfolio.analyzer.testsupport.AppRwIntegrationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Database-enforced tenant isolation for {@code app_rw}.
 *
 * <p>The three methods {@code missingAppTenantIdReturnsNoRows},
 * {@code setLocalTenantIdDoesNotLeakAcrossPooledTransactions} and
 * {@code rowLevelSecurityIsolatesTenantsWithoutExplicitWhere} match
 * {@code RowLevelSecurityIT} on {@code test/it-enhancements} (#9). That class
 * is still {@code @Disabled("waiting V3 RLS")} and is not on main, so this IT
 * owns the container. After #9 merges, rebase onto it: extend
 * {@code AbstractPgVectorIT}, delete {@code AppRwRoleBootstrap} (V3 creates
 * {@code app_rw} and this IT injects the password through the Flyway
 * placeholder), drop the container and stubs below, and keep these methods.
 * Do not grant {@code TRUNCATE} to {@code app_rw}; #9's
 * {@code AbstractPgVectorIT} reset must switch to the owner connection.
 *
 * <p>Counts that expect zero rows run inside a transaction that does not call
 * {@code set_config}. Tenant-table SQL outside a transaction is rejected.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("test")
class RowLevelSecurityIT {

    private static final String INSERT_DOCUMENT =
            "INSERT INTO document (document_id, tenant_id, owner_user_id, embedding_model, embedding_dim) "
                    + "VALUES (?, ?, ?, 'text-embedding-v4', 1024)";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        AppRwIntegrationSupport.register(registry, postgres);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private RagQueryService queryService;

    @Autowired
    private VectorStorePort vectorStore;

    private JdbcTemplate owner;

    @BeforeEach
    void resetTables() {
        owner = AppRwIntegrationSupport.ownerJdbc(postgres);
        owner.update("TRUNCATE TABLE chunk, document");
        clearPrincipal();
    }

    @AfterEach
    void clearPrincipalAfter() {
        clearPrincipal();
    }

    @Test
    void applicationUserIsAppRw() {
        assertThat(jdbcTemplate.queryForObject("SELECT current_user", String.class))
                .isEqualTo(AppRwIntegrationSupport.APP_RW_USER);
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
        Integer visible = withoutTenant(() ->
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class));
        assertThat(visible).isZero();
        Integer documents = withoutTenant(() ->
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document", Integer.class));
        assertThat(documents).isZero();
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class)).isEqualTo(1);
    }

    @Test
    void setLocalTenantIdDoesNotLeakAcrossPooledTransactions() {
        for (int i = 0; i < 30; i++) {
            String tenant = i % 2 == 0 ? "tenant-rls-a" : "tenant-rls-b";
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(currentSetting()).isNullOrEmpty();
                jdbcTemplate.queryForObject(
                        "SELECT set_config('app.tenant_id', ?, true)", String.class, tenant);
                insertChunkInCurrentTransaction(tenant, "local-" + tenant);
                assertThat(currentSetting()).isEqualTo(tenant);
            });
        }
        String setting = currentSetting();
        assertThat(setting == null || setting.isBlank()).isTrue();
        Integer visible = withoutTenant(() ->
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class));
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

    @Test
    void withCheckRejectsInsertForAnotherTenant() {
        assertThatThrownBy(() -> inTransaction("tenant-a", () ->
                jdbcTemplate.update(INSERT_DOCUMENT, UUID.randomUUID(), "tenant-b", "user-b")))
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> assertThat(messages(error)).contains("row-level security"));

        inTransaction("tenant-a", () -> insertChunkInCurrentTransaction("tenant-a", "owned-by-a"));
        assertThatThrownBy(() -> inTransaction("tenant-a", () -> jdbcTemplate.update(
                "INSERT INTO chunk (tenant_id, doc_id, chunk_index, source_type, owner_user_id, "
                        + "embedding_model, embedding_dim, content, embedding) "
                        + "VALUES ('tenant-b', ?, 0, 'text', 'user-b', 'text-embedding-v4', 1024, 'x', ?::vector)",
                owner.queryForObject("SELECT document_id FROM document LIMIT 1", UUID.class),
                zeroVector(1024))))
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> assertThat(messages(error)).contains("row-level security"));

        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM document WHERE tenant_id = 'tenant-b'", Integer.class)).isZero();
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM chunk WHERE tenant_id = 'tenant-b'", Integer.class)).isZero();
    }

    @Test
    void crossTenantForeignKeyStillRejectedUnderAppRw() {
        UUID docId = UUID.randomUUID();
        inTransaction("tenant-a", () -> jdbcTemplate.update(INSERT_DOCUMENT, docId, "tenant-a", "user-a"));

        assertThatThrownBy(() -> inTransaction("tenant-b", () -> jdbcTemplate.update(
                "INSERT INTO chunk (tenant_id, doc_id, chunk_index, source_type, owner_user_id, "
                        + "embedding_model, embedding_dim, content, embedding) "
                        + "VALUES ('tenant-b', ?, 0, 'text', 'user-b', 'text-embedding-v4', 1024, 'x', ?::vector)",
                docId, zeroVector(1024))))
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> assertThat(messages(error)).contains("fk_chunk_document"));
    }

    @Test
    void tenantACannotSearchTenantB() throws Exception {
        TestableTenantContext ctx = context();
        TenantPrincipal a = principal("tenant-a", "user-a", "group-a");
        TenantPrincipal b = principal("tenant-b", "user-b", "group-b");
        ctx.setPrincipal(a);
        ingestionService.ingestDocument(a, textFile("secret-a.txt", "ALPHA confidential payload"), "src-a");
        ctx.setPrincipal(b);
        ingestionService.ingestDocument(b, textFile("secret-b.txt", "BETA confidential payload"), "src-b");

        ctx.setPrincipal(a);
        RagQueryResponse seenByA = queryService.queryWithContext(a, "confidential");
        assertThat(seenByA.getSources()).isNotEmpty();
        assertThat(seenByA.getSources()).allSatisfy(source -> {
            assertThat(source.getExcerpt()).contains("ALPHA");
            assertThat(source.getExcerpt()).doesNotContain("BETA");
        });

        ctx.setPrincipal(b);
        RagQueryResponse seenByB = queryService.queryWithContext(b, "ALPHA");
        assertThat(seenByB.getSources()).isNotEmpty();
        assertThat(seenByB.getSources()).allSatisfy(source -> {
            assertThat(source.getExcerpt()).contains("BETA");
            assertThat(source.getExcerpt()).doesNotContain("ALPHA");
        });
    }

    @Test
    void sameGroupNameDoesNotCrossTenants() throws Exception {
        TestableTenantContext ctx = context();
        TenantPrincipal a = principal("tenant-a", "user-a", "g1");
        TenantPrincipal b = principal("tenant-b", "user-b", "g1");
        ctx.setPrincipal(a);
        ingestionService.ingestDocument(a, textFile("a.txt", "ALPHA group document"), "src");
        ctx.setPrincipal(b);
        ingestionService.ingestDocument(b, textFile("b.txt", "BETA group document"), "src");

        ctx.setPrincipal(a);
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
        TestableTenantContext ctx = context();
        TenantPrincipal a = principal("tenant-a", "user-a", "g1");
        TenantPrincipal b = principal("tenant-b", "user-b", "g1");

        ctx.setPrincipal(a);
        var own = ingestionService.ingestDocument(a, textFile("own.txt", "ALPHA own document"), "src");
        String ownId = String.valueOf(own.get("documentId"));
        ctx.setPrincipal(b);
        var foreign = ingestionService.ingestDocument(b, textFile("foreign.txt", "BETA foreign document"), "src");
        UUID foreignId = UUID.fromString(String.valueOf(foreign.get("documentId")));

        ctx.setPrincipal(a);
        assertThat(vectorStore.deleteChunksByDocument(a, foreignId)).isZero();
        vectorStore.updateDocumentAcl(a, foreignId, "user-a", Set.of("g1"));
        ingestionService.deleteDocument(a, foreignId.toString());

        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM document WHERE document_id = ?", Integer.class, foreignId)).isEqualTo(1);
        assertThat(owner.queryForObject(
                "SELECT owner_user_id FROM document WHERE document_id = ?", String.class, foreignId))
                .isEqualTo("user-b");
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM chunk WHERE doc_id = ?", Integer.class, foreignId)).isGreaterThan(0);

        ctx.setPrincipal(b);
        assertThat(queryService.queryWithContext(b, "BETA").getSources()).isNotEmpty();

        ctx.setPrincipal(a);
        ingestionService.deleteDocument(a, ownId);
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM document WHERE document_id = ?::uuid",
                Integer.class, ownId)).isZero();
    }

    @Test
    void concurrentTenantsDoNotCrossOnThePool() throws Exception {
        inTransaction("tenant-a", () -> jdbcTemplate.update(INSERT_DOCUMENT, UUID.randomUUID(), "tenant-a", "user-a"));
        inTransaction("tenant-b", () -> jdbcTemplate.update(INSERT_DOCUMENT, UUID.randomUUID(), "tenant-b", "user-b"));

        TestableTenantContext ctx = context();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?>[] tasks = new Future<?>[100];
            for (int i = 0; i < tasks.length; i++) {
                String tenant = i % 2 == 0 ? "tenant-a" : "tenant-b";
                tasks[i] = pool.submit(() -> {
                    ctx.setPrincipal(principal(tenant, "user-" + tenant, "g1"));
                    try {
                        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                            assertThat(currentSetting()).isEqualTo(tenant);
                            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document", Integer.class))
                                    .isEqualTo(1);
                            assertThat(jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM document WHERE tenant_id <> ?", Integer.class, tenant))
                                    .isZero();
                        });
                    } finally {
                        ctx.clear();
                    }
                });
            }
            for (Future<?> task : tasks) {
                task.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void insertChunk(String tenantId, String content) {
        inTransaction(tenantId, () -> insertChunkInCurrentTransaction(tenantId, content));
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

    private void inTransaction(String tenantId, Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class,
                    tenantId);
            work.run();
        });
    }

    private <T> T withoutTenant(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
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

    private TestableTenantContext context() {
        return (TestableTenantContext) tenantContext;
    }

    private void clearPrincipal() {
        if (tenantContext instanceof TestableTenantContext ctx) {
            ctx.clear();
        }
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

    /**
     * Rebase: delete this nested configuration. #9 already supplies
     * {@code ItStubsConfig} from {@code AbstractPgVectorIT}.
     */
    @TestConfiguration
    static class Stubs {
        @Bean
        @Primary
        EmbeddingModel stubEmbeddingModel() {
            EmbeddingModel model = mock(EmbeddingModel.class);
            when(model.embed(anyString())).thenAnswer(invocation -> {
                float[] embedding = new float[1024];
                java.util.Arrays.fill(embedding, 1.0f);
                String text = invocation.getArgument(0);
                int hash = text == null ? 0 : text.hashCode();
                embedding[Math.floorMod(hash, 1024)] += 0.05f;
                double norm = 0.0;
                for (float value : embedding) {
                    norm += (double) value * value;
                }
                norm = Math.sqrt(norm);
                for (int i = 0; i < embedding.length; i++) {
                    embedding[i] /= (float) norm;
                }
                return embedding;
            });
            when(model.dimensions()).thenReturn(1024);
            return model;
        }

        @Bean
        @Primary
        TenantContext testTenantContext() {
            return new TestableTenantContext();
        }

        @Bean
        @Primary
        MultimodalAIClient stubMultimodalAIClient() {
            MultimodalAIClient client = mock(MultimodalAIClient.class);
            when(client.chat(anyString())).thenReturn("stub answer");
            when(client.getTextModelName()).thenReturn("qwen-plus");
            when(client.getModelName()).thenReturn("qwen-vl-max");
            return client;
        }
    }

    static final class TestableTenantContext implements TenantContext {
        private final ThreadLocal<TenantPrincipal> principal = new ThreadLocal<>();

        void setPrincipal(TenantPrincipal value) {
            principal.set(value);
        }

        void clear() {
            principal.remove();
        }

        @Override
        public String getTenantId() {
            TenantPrincipal current = principal.get();
            return current == null ? null : current.tenantId();
        }

        @Override
        public String getUserId() {
            TenantPrincipal current = principal.get();
            return current == null ? null : current.userId();
        }

        @Override
        public Set<String> getRoles() {
            TenantPrincipal current = principal.get();
            return current == null ? Set.of() : current.roles();
        }

        @Override
        public Set<String> getGroups() {
            TenantPrincipal current = principal.get();
            return current == null ? Set.of() : current.groups();
        }

        @Override
        public TenantPrincipal current() {
            TenantPrincipal current = principal.get();
            if (current == null) {
                throw new IllegalStateException("TenantPrincipal not set in test");
            }
            return current;
        }
    }
}
