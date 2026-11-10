package com.portfolio.analyzer.repository;

import com.portfolio.analyzer.common.ErrorCode;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.exception.BusinessException;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.security.TenantSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JdbcTemplate实现的VectorStorePort
 * 
 * <p>所有chunk表的读写操作都通过此类。
 * 
 * <p><strong>安全实现：</strong>
 * <ul>
 *   <li>所有SQL WHERE子句显式过滤 tenant_id 和 ACL</li>
 *   <li>使用 @Transactional，事务开始时由 {@link TenantSession} 设置 app.tenant_id</li>
 *   <li>检查pgvector版本，配置HNSW参数优化过滤查询</li>
 * </ul>
 * 
 * @since Stage 2 - VectorStore port
 */
@Repository
public class JdbcVectorStorePort implements VectorStorePort {
    
    private static final Logger logger = LoggerFactory.getLogger(JdbcVectorStorePort.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final RagConfig ragConfig;
    private final TenantSession tenantSession;
    private final boolean supportsIterativeScan;
    
    public JdbcVectorStorePort(JdbcTemplate jdbcTemplate, RagConfig ragConfig) {
        this(jdbcTemplate, ragConfig, null, null);
    }

    /**
     * Flyway 开启时先触发迁移，再探测 pgvector 版本。
     * 版本探测失败只打 WARN（不带堆栈）；堆栈在 DEBUG。
     */
    @Autowired
    public JdbcVectorStorePort(JdbcTemplate jdbcTemplate,
                               RagConfig ragConfig,
                               ObjectProvider<FlywayMigrationInitializer> flywayMigration,
                               TenantSession tenantSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragConfig = ragConfig;
        this.tenantSession = tenantSession;
        if (flywayMigration != null) {
            flywayMigration.ifAvailable(initializer ->
                    logger.debug("Flyway migrations applied before pgvector version check"));
        }
        this.supportsIterativeScan = checkPgVectorVersion();
    }
    
    /**
     * 检查pgvector版本是否>=0.8.0（支持hnsw.iterative_scan）
     */
    private boolean checkPgVectorVersion() {
        try {
            String version = jdbcTemplate.queryForObject(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                    String.class
            );
            if (version == null) {
                logger.warn("pgvector extension not found, HNSW iterative_scan disabled");
                return false;
            }
            
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            
            boolean supports = major > 0 || (major == 0 && minor >= 8);
            logger.info("pgvector version: {}, iterative_scan support: {}", version, supports);
            return supports;
            
        } catch (Exception e) {
            logger.warn("Failed to check pgvector version, HNSW iterative_scan disabled: {}", e.toString());
            logger.debug("pgvector version check failed", e);
            return false;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<SearchResult> searchSimilar(
            TenantPrincipal principal,
            float[] queryEmbedding,
            int topK,
            double similarityThreshold) {
        
        setTenantContext(principal.tenantId());
        
        // Configure HNSW parameters using set_config (transaction-local)
        configureHnswParameters();
        
        // 构建查询向量
        PGobject queryVector = createPgVector(queryEmbedding);
        
        // 计算距离阈值：similarity >= threshold => distance <= (1 - threshold)
        double distanceThreshold = 1.0 - similarityThreshold;
        
        // SQL WHERE: tenant_id = ? AND (owner_user_id = ? OR acl_groups && ?::text[])
        // ORDER BY embedding <=> query_vector, LIMIT topK
        String sql = """
                SELECT id, content, page_no, source_type, 
                       embedding <=> ?::vector AS distance,
                       metadata
                FROM chunk
                WHERE tenant_id = ?
                  AND (owner_user_id = ? OR acl_groups && ?::text[])
                  AND (embedding <=> ?::vector) <= ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        
        String[] groupsArray = principal.groups().toArray(new String[0]);
        
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapSearchResult(rs),
                queryVector, // distance calculation 1
                principal.tenantId(),
                principal.userId(),
                textArray(groupsArray),
                queryVector, // distance filter
                distanceThreshold,
                queryVector, // ORDER BY
                topK
        );
    }
    
    @Override
    @Transactional
    public int upsertChunks(TenantPrincipal principal, List<ChunkData> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        
        setTenantContext(principal.tenantId());
        
        String sql = """
                INSERT INTO chunk (
                    tenant_id, doc_id, page_no, chunk_index, source_type,
                    owner_user_id, acl_groups, embedding_model, embedding_dim,
                    content, metadata, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector)
                ON CONFLICT (tenant_id, doc_id, chunk_index)
                DO UPDATE SET
                    page_no = EXCLUDED.page_no,
                    source_type = EXCLUDED.source_type,
                    owner_user_id = EXCLUDED.owner_user_id,
                    acl_groups = EXCLUDED.acl_groups,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_dim = EXCLUDED.embedding_dim,
                    content = EXCLUDED.content,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding,
                    created_at = CURRENT_TIMESTAMP
                """;
        
        String[] groupsArray = principal.groups().toArray(new String[0]);
        int totalAffected = 0;
        
        for (ChunkData chunk : chunks) {
            PGobject embeddingVector = createPgVector(chunk.embedding());
            
            int affected = jdbcTemplate.update(
                    sql,
                    principal.tenantId(),
                    chunk.docId(),
                    chunk.pageNo(),
                    chunk.chunkIndex(),
                    chunk.sourceType(),
                    principal.userId(),
                    textArray(groupsArray),
                    chunk.embeddingModel(),
                    chunk.embeddingDim(),
                    chunk.content(),
                    chunk.metadata(),
                    embeddingVector
            );
            totalAffected += affected;
        }
        
        logger.debug("Upserted {} chunks for tenant={}, doc={}", 
                totalAffected, principal.tenantId(), chunks.get(0).docId());
        
        return totalAffected;
    }
    
    @Override
    @Transactional
    public int deleteChunksByDocument(TenantPrincipal principal, UUID docId) {
        // Set tenant context for RLS
        setTenantContext(principal.tenantId());
        
        String sql = "DELETE FROM chunk WHERE tenant_id = ? AND doc_id = ?";
        
        int deleted = jdbcTemplate.update(sql, principal.tenantId(), docId);
        
        logger.debug("Deleted {} chunks for tenant={}, doc={}", 
                deleted, principal.tenantId(), docId);
        
        return deleted;
    }
    
    @Override
    @Transactional
    public void updateDocumentAcl(TenantPrincipal principal, UUID docId, 
                                   String ownerUserId, Set<String> aclGroups) {
        if (ownerUserId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ownerUserId must not be null");
        }

        // Set tenant context for RLS
        setTenantContext(principal.tenantId());
        
        // Convert groups to SQL array
        String[] groupsArray = aclGroups != null ? aclGroups.toArray(new String[0]) : new String[0];
        
        // Update document table
        String docSql = """
                UPDATE document 
                SET owner_user_id = ?, acl_groups = ?
                WHERE tenant_id = ? AND document_id = ?
                """;
        
        int docUpdated = jdbcTemplate.update(docSql, ownerUserId, textArray(groupsArray), 
                                              principal.tenantId(), docId);
        
        if (docUpdated == 0) {
            logger.warn("Document not found for ACL update: tenant={}, doc={}", 
                       principal.tenantId(), docId);
            return;
        }
        
        // Update all chunks for this document (synchronous, immediate effect)
        String chunkSql = """
                UPDATE chunk 
                SET owner_user_id = ?, acl_groups = ?
                WHERE tenant_id = ? AND doc_id = ?
                """;
        
        int chunksUpdated = jdbcTemplate.update(chunkSql, ownerUserId, textArray(groupsArray),
                                                 principal.tenantId(), docId);
        
        logger.info("Updated ACL: tenant={}, doc={}, owner={}, groups={}, chunks={}", 
                   principal.tenantId(), docId, ownerUserId, aclGroups, chunksUpdated);
    }
    
    /**
     * 与 {@link TenantSession} 使用同一条 {@code set_config}。重复设置是幂等的。
     * 显式 {@code WHERE tenant_id = ?} 仍然保留。
     */
    private void setTenantContext(String tenantId) {
        if (tenantSession == null) {
            logger.trace("Tenant context {} (TenantSession not wired)", tenantId);
            return;
        }
        tenantSession.apply(tenantId);
    }
    
    /**
     * 配置HNSW查询参数（使用set_config）
     * 
     * <p>在事务中设置LOCAL变量，仅影响当前事务：
     * <ul>
     *   <li>hnsw.ef_search: 控制HNSW搜索的候选集大小（默认100）</li>
     *   <li>hnsw.iterative_scan = relaxed_order: pgvector >= 0.8支持，优化过滤查询</li>
     * </ul>
     */
    private void configureHnswParameters() {
        int efSearch = ragConfig.getHnswEfSearch();
        
        // Use set_config for transaction-local settings (better than SET LOCAL for bind parameters)
        jdbcTemplate.queryForObject(
                "SELECT set_config('hnsw.ef_search', ?, true)", 
                String.class, 
                String.valueOf(efSearch)
        );
        
        if (supportsIterativeScan) {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('hnsw.iterative_scan', 'relaxed_order', true)",
                    String.class
            );
            logger.debug("HNSW configured: ef_search={}, iterative_scan=relaxed_order", efSearch);
        } else {
            logger.debug("HNSW configured: ef_search={}", efSearch);
        }
    }
    
    /**
     * text[] 必须在当前事务连接上创建，String[] 不能可靠绑定到 PostgreSQL 数组。
     */
    private Array textArray(String[] values) {
        String[] safe = values == null ? new String[0] : values;
        return jdbcTemplate.execute((ConnectionCallback<Array>) connection ->
                connection.createArrayOf("text", safe));
    }

    /**
     * 创建PGobject表示向量
     */
    private PGobject createPgVector(float[] embedding) {
        try {
            PGobject vector = new PGobject();
            vector.setType("vector");
            
            // 格式: "[1.0,2.0,3.0]"
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < embedding.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(embedding[i]);
            }
            sb.append("]");
            vector.setValue(sb.toString());
            
            return vector;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create PG vector", e);
        }
    }
    
    /**
     * 映射ResultSet到SearchResult
     */
    private SearchResult mapSearchResult(ResultSet rs) throws SQLException {
        return new SearchResult(
                UUID.fromString(rs.getString("id")),
                rs.getString("content"),
                (Integer) rs.getObject("page_no"),
                rs.getString("source_type"),
                rs.getDouble("distance"),
                rs.getString("metadata")
        );
    }
}
