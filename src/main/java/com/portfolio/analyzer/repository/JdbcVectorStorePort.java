package com.portfolio.analyzer.repository;

import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.security.TenantPrincipal;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate实现的VectorStorePort
 * 
 * <p>所有chunk表的读写操作都通过此类。
 * 
 * <p><strong>安全实现：</strong>
 * <ul>
 *   <li>所有SQL WHERE子句显式过滤 tenant_id 和 ACL</li>
 *   <li>使用 @Transactional，后续可添加 SET LOCAL app.tenant_id</li>
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
    private final boolean supportsIterativeScan;
    
    public JdbcVectorStorePort(JdbcTemplate jdbcTemplate, RagConfig ragConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragConfig = ragConfig;
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
            logger.warn("Failed to check pgvector version, HNSW iterative_scan disabled", e);
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
        
        // 配置HNSW参数（在事务中设置LOCAL变量）
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
                groupsArray,
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
                    groupsArray,
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
        String sql = "DELETE FROM chunk WHERE tenant_id = ? AND doc_id = ?";
        
        int deleted = jdbcTemplate.update(sql, principal.tenantId(), docId);
        
        logger.debug("Deleted {} chunks for tenant={}, doc={}", 
                deleted, principal.tenantId(), docId);
        
        return deleted;
    }
    
    /**
     * 配置HNSW查询参数
     * 
     * <p>在事务中设置LOCAL变量，仅影响当前事务：
     * <ul>
     *   <li>hnsw.ef_search: 控制HNSW搜索的候选集大小（默认100）</li>
     *   <li>hnsw.iterative_scan = relaxed_order: pgvector >= 0.8支持，优化过滤查询</li>
     * </ul>
     */
    private void configureHnswParameters() {
        int efSearch = ragConfig.getHnswEfSearch();
        jdbcTemplate.execute("SET LOCAL hnsw.ef_search = " + efSearch);
        
        if (supportsIterativeScan) {
            jdbcTemplate.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
            logger.debug("HNSW configured: ef_search={}, iterative_scan=relaxed_order", efSearch);
        } else {
            logger.debug("HNSW configured: ef_search={}", efSearch);
        }
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
