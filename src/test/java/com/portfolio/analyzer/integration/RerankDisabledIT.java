package com.portfolio.analyzer.integration;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.security.TenantContext;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 重排关闭时，查询结果与直接按 topK 做向量检索一致，跨租户仍然 0 条。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RerankDisabledIT {

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
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("app.rag.rerank.enabled", () -> "false");
        registry.add("app.rag.top-k", () -> "3");
        registry.add("app.rag.similarity-threshold", () -> "0.7");
        registry.add("app.rag.recall-top-n", () -> "20");
        registry.add("app.rag.rerank.base-url", () -> "http://127.0.0.1:9");
    }

    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public EmbeddingModel stubEmbeddingModel(RagConfig ragConfig) {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            assertThat(ragConfig.getEmbeddingDimensions()).isEqualTo(1024);
            assertThat(ragConfig.getRerank().isEnabled()).isFalse();
            when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
                String text = invocation.getArgument(0);
                float[] embedding = new float[1024];
                java.util.Arrays.fill(embedding, 1.0f);
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
            when(embeddingModel.dimensions()).thenReturn(1024);
            return embeddingModel;
        }

        @Bean
        @Primary
        public TenantContext testTenantContext() {
            return new TestableTenantContext();
        }

        @Bean
        @Primary
        public MultimodalAIClient stubMultimodalAIClient() {
            MultimodalAIClient client = mock(MultimodalAIClient.class);
            when(client.chat(anyString())).thenReturn("stub answer 【数据块1】");
            when(client.getTextModelName()).thenReturn("qwen-plus");
            when(client.getModelName()).thenReturn("qwen-vl-max");
            return client;
        }
    }

    static class TestableTenantContext implements TenantContext {
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
            return current != null ? current.tenantId() : null;
        }

        @Override
        public String getUserId() {
            TenantPrincipal current = principal.get();
            return current != null ? current.userId() : null;
        }

        @Override
        public Set<String> getRoles() {
            TenantPrincipal current = principal.get();
            return current != null ? current.roles() : Set.of();
        }

        @Override
        public Set<String> getGroups() {
            TenantPrincipal current = principal.get();
            return current != null ? current.groups() : Set.of();
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

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private RagQueryService queryService;

    @Autowired
    private VectorStorePort vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private RagConfig ragConfig;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void disabledRerankMatchesDirectTopKSearch() throws Exception {
        TestableTenantContext context = (TestableTenantContext) tenantContext;
        TenantPrincipal owner = new TenantPrincipal("rerank-off-owner", "owner-user", Set.of(), Set.of("group-a"));
        context.setPrincipal(owner);
        try {
            for (int i = 0; i < 5; i++) {
                MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "note-" + i + ".txt",
                        "text/plain",
                        ("Rerank disabled note " + i + " about portable widgets and springs.").getBytes()
                );
                ingestionService.ingestDocument(owner, file, "rerank-off");
            }

            Integer stored = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chunk WHERE tenant_id = ?",
                    Integer.class,
                    owner.tenantId());
            assertThat(stored).isGreaterThan(ragConfig.getTopK());
            assertThat(ragConfig.getRerank().isEnabled()).isFalse();

            String question = "portable widgets";
            RagQueryResponse response = queryService.queryWithContext(owner, question);
            List<VectorStorePort.SearchResult> direct = vectorStore.searchSimilar(
                    owner,
                    embeddingModel.embed(question),
                    ragConfig.getTopK(),
                    ragConfig.getSimilarityThreshold());

            assertThat(response.isReranked()).isFalse();
            assertThat(response.getSources()).hasSize(direct.size());
            assertThat(direct).hasSize(ragConfig.getTopK());
            assertThat(response.getModel()).isEqualTo("qwen-plus");
            List<String> excerpts = new ArrayList<>();
            for (int i = 0; i < direct.size(); i++) {
                VectorStorePort.SearchResult hit = direct.get(i);
                RagQueryResponse.DocumentSource source = response.getSources().get(i);
                assertThat(source.getSimilarity()).isEqualTo(hit.similarity());
                assertThat(source.getPageNumber()).isEqualTo(hit.pageNo());
                String content = hit.content();
                String expectedExcerpt = content.length() > 200
                        ? content.substring(0, 200) + "..."
                        : content;
                assertThat(source.getExcerpt()).isEqualTo(expectedExcerpt);
                excerpts.add(source.getExcerpt());
            }
            assertThat(excerpts).doesNotHaveDuplicates();
        } finally {
            context.clear();
        }
    }

    @Test
    void crossTenantQueryReturnsZeroHits() throws Exception {
        TestableTenantContext context = (TestableTenantContext) tenantContext;
        TenantPrincipal owner = new TenantPrincipal("rerank-cross-a", "user-a", Set.of(), Set.of("group-a"));
        context.setPrincipal(owner);
        try {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "secret-a.txt",
                    "text/plain",
                    "Tenant A confidential rerank material.".getBytes()
            );
            ingestionService.ingestDocument(owner, file, "secret-a");

            RagQueryResponse own = queryService.queryWithContext(owner, "confidential rerank");
            assertThat(own.isReranked()).isFalse();
            assertThat(own.getSources()).isNotEmpty();

            TenantPrincipal other = new TenantPrincipal("rerank-cross-b", "user-b", Set.of(), Set.of("group-b"));
            context.setPrincipal(other);
            RagQueryResponse foreign = queryService.queryWithContext(other, "confidential rerank");
            assertThat(foreign.getSources()).isEmpty();
            assertThat(foreign.isReranked()).isFalse();
            assertThat(foreign.getAnswer()).contains("没有找到相关信息");

            List<VectorStorePort.SearchResult> direct = vectorStore.searchSimilar(
                    other,
                    embeddingModel.embed("confidential rerank"),
                    ragConfig.getTopK(),
                    ragConfig.getSimilarityThreshold());
            assertThat(direct).isEmpty();
        } finally {
            context.clear();
        }
    }
}
