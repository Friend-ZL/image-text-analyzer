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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenant delete, ACL, and idempotent upsert against the real chunk table.
 */
class VectorStoreBehaviorIT extends AbstractPgVectorIT {

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private RagQueryService queryService;

    @Autowired
    private VectorStorePort vectorStore;

    @Test
    void crossTenantDeleteAffectsZeroRows() throws Exception {
        TenantPrincipal owner = new TenantPrincipal("tenant-del-a", "user-a", Set.of(), Set.of("shared"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "owned.txt",
                "text/plain",
                "tenant A secret that B must not delete".getBytes(StandardCharsets.UTF_8)
        );
        Map<String, Object> ingested = ingestionService.ingestDocument(owner, file, "delete-source");
        UUID docId = UUID.fromString(String.valueOf(ingested.get("documentId")));

        Integer before = countChunks(owner.tenantId(), docId);
        assertThat(before).isPositive();

        TenantPrincipal other = new TenantPrincipal("tenant-del-b", "user-b", Set.of(), Set.of("shared"));
        int deleted = vectorStore.deleteChunksByDocument(other, docId);
        assertThat(deleted).isZero();
        assertThat(countChunks(owner.tenantId(), docId)).isEqualTo(before);
        assertThat(countChunks(other.tenantId(), docId)).isZero();

        RagQueryResponse stillVisible = queryService.queryWithContext(owner, "secret");
        assertThat(stillVisible.getSources()).isNotEmpty();
    }

    @Test
    void emptyGroupsUserSeesOnlyOwnDocuments() throws Exception {
        TenantPrincipal owner = new TenantPrincipal("tenant-empty-acl", "owner-1", Set.of(), Set.of("editors"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "memo.txt",
                "text/plain",
                "owner-only secret memo for the empty-groups case".getBytes(StandardCharsets.UTF_8)
        );
        ingestionService.ingestDocument(owner, file, "acl");

        TenantPrincipal ownerWithoutGroups = new TenantPrincipal("tenant-empty-acl", "owner-1", Set.of(), Set.of());
        assertThat(queryService.queryWithContext(ownerWithoutGroups, "secret memo").getSources()).isNotEmpty();

        TenantPrincipal strangerWithoutGroups = new TenantPrincipal("tenant-empty-acl", "stranger", Set.of(), Set.of());
        assertThat(queryService.queryWithContext(strangerWithoutGroups, "secret memo").getSources()).isEmpty();
    }

    @Test
    void idempotentUpsertUpdatesContentWithoutAddingRows() {
        TenantPrincipal principal = new TenantPrincipal("tenant-idem", "user-idem", Set.of(), Set.of("g"));
        UUID docId = UUID.randomUUID();
        insertDocument(docId, principal);

        int inserted = vectorStore.upsertChunks(principal, List.of(chunk(docId, 0, "version-one")));
        assertThat(inserted).isEqualTo(1);
        assertThat(countChunks(principal.tenantId(), docId)).isEqualTo(1);

        int updated = vectorStore.upsertChunks(principal, List.of(chunk(docId, 0, "version-two")));
        assertThat(updated).isEqualTo(1);
        assertThat(countChunks(principal.tenantId(), docId)).isEqualTo(1);
        assertThat(content(principal.tenantId(), docId)).isEqualTo("version-two");
    }

    @Test
    void sameDocIdUnderDifferentTenantsDoesNotOverwrite() {
        TenantPrincipal tenantA = new TenantPrincipal("tenant-up-a", "user-a", Set.of(), Set.of());
        TenantPrincipal tenantB = new TenantPrincipal("tenant-up-b", "user-b", Set.of(), Set.of());
        UUID docId = UUID.randomUUID();
        insertDocument(docId, tenantA);
        vectorStore.upsertChunks(tenantA, List.of(chunk(docId, 0, "alpha")));

        String conflict = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'uq_chunk_tenant_doc_index'",
                String.class
        );
        assertThat(conflict).contains("tenant_id").contains("doc_id").contains("chunk_index");

        // document.document_id is a global primary key, so tenant B cannot store a
        // second document row for this id. The chunk FK (tenant_id, doc_id) rejects
        // the write. Tenant A's row must stay as it was.
        assertThatThrownBy(() -> vectorStore.upsertChunks(tenantB, List.of(chunk(docId, 0, "beta"))))
                .isInstanceOf(DataAccessException.class);

        assertThat(content(tenantA.tenantId(), docId)).isEqualTo("alpha");
        assertThat(countChunks(tenantA.tenantId(), docId)).isEqualTo(1);
        assertThat(countChunks(tenantB.tenantId(), docId)).isZero();
        Integer rowsForDoc = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk WHERE doc_id = ?",
                Integer.class,
                docId
        );
        assertThat(rowsForDoc).isEqualTo(1);
    }

    private void insertDocument(UUID docId, TenantPrincipal principal) {
        jdbcTemplate.update(
                "INSERT INTO document (document_id, tenant_id, owner_user_id, embedding_model, embedding_dim) " +
                        "VALUES (?, ?, ?, 'text-embedding-v4', 1024)",
                docId, principal.tenantId(), principal.userId()
        );
    }

    private VectorStorePort.ChunkData chunk(UUID docId, int chunkIndex, String content) {
        float[] embedding = new float[1024];
        java.util.Arrays.fill(embedding, 0.01f);
        return new VectorStorePort.ChunkData(
                docId,
                1,
                chunkIndex,
                "text",
                content,
                "text-embedding-v4",
                1024,
                embedding,
                "{}"
        );
    }

    private Integer countChunks(String tenantId, UUID docId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk WHERE tenant_id = ? AND doc_id = ?",
                Integer.class,
                tenantId,
                docId
        );
    }

    private String content(String tenantId, UUID docId) {
        return jdbcTemplate.queryForObject(
                "SELECT content FROM chunk WHERE tenant_id = ? AND doc_id = ? AND chunk_index = 0",
                String.class,
                tenantId,
                docId
        );
    }
}
