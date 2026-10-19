package com.portfolio.analyzer.repository;

import com.portfolio.analyzer.security.TenantPrincipal;

import java.util.List;
import java.util.UUID;

/**
 * Vector Store Port接口
 * 
 * <p>封装chunk表的所有读写操作（ADR-002的migration seam）。
 * 所有对chunk表的访问必须通过此接口，不允许其他代码直接操作chunk表。
 * 
 * <p><strong>安全保证：</strong>
 * <ul>
 *   <li>所有方法接受 {@link TenantPrincipal} 参数，确保租户隔离</li>
 *   <li>所有SQL WHERE子句显式过滤 tenant_id 和 ACL（owner_user_id / acl_groups）</li>
 *   <li>方法带 @Transactional，后续可在事务中添加 SET LOCAL app.tenant_id</li>
 *   <li>Backend将添加RLS策略，显式WHERE作为defense in depth</li>
 * </ul>
 * 
 * @since Stage 2 - VectorStore port
 */
public interface VectorStorePort {
    
    /**
     * 向量相似度搜索结果
     * 
     * @param id chunk ID
     * @param content chunk内容
     * @param pageNo 页码（可选）
     * @param sourceType 来源类型
     * @param distance 余弦距离（0到2，越小越相似）
     * @param metadata 非安全字段的元数据（file_name, content_type等）
     */
    record SearchResult(
            UUID id,
            String content,
            Integer pageNo,
            String sourceType,
            double distance,
            String metadata
    ) {
        public double similarity() {
            return 1.0 - distance;
        }
    }
    
    /**
     * Chunk数据（用于插入/更新）
     * 
     * @param docId 文档ID
     * @param pageNo 页码
     * @param chunkIndex chunk索引（同一文档内唯一，用于幂等）
     * @param sourceType 来源类型
     * @param content 内容
     * @param embeddingModel embedding模型名称
     * @param embeddingDim embedding维度
     * @param embedding embedding向量（必须是embeddingDim维）
     * @param metadata 非安全字段的元数据（可选）
     */
    record ChunkData(
            UUID docId,
            Integer pageNo,
            int chunkIndex,
            String sourceType,
            String content,
            String embeddingModel,
            int embeddingDim,
            float[] embedding,
            String metadata
    ) {}
    
    /**
     * 向量相似度搜索
     * 
     * <p>使用pgvector的余弦距离（<=>）查找最相似的chunks。
     * 在SQL WHERE中过滤：tenant_id = ? AND (owner_user_id = ? OR acl_groups && ?::text[])
     * 
     * @param principal 租户主体（确定租户、用户和授权组）
     * @param queryEmbedding 查询向量（1024维）
     * @param topK 返回最多topK个结果
     * @param similarityThreshold 相似度阈值（0到1，过滤掉低于此值的结果）
     * @return 搜索结果列表（按相似度降序，最多topK个）
     */
    List<SearchResult> searchSimilar(
            TenantPrincipal principal,
            float[] queryEmbedding,
            int topK,
            double similarityThreshold
    );
    
    /**
     * Upsert chunks（幂等操作）
     * 
     * <p>基于 UNIQUE (tenant_id, doc_id, chunk_index) 约束，使用 ON CONFLICT 实现幂等。
     * 如果 (tenant_id, doc_id, chunk_index) 已存在，更新所有字段；否则插入新记录。
     * 
     * <p>tenant_id、owner_user_id、acl_groups 从 principal 提取。
     * 
     * @param principal 租户主体
     * @param chunks chunk数据列表
     * @return 受影响的行数
     */
    int upsertChunks(TenantPrincipal principal, List<ChunkData> chunks);
    
    /**
     * 删除文档的所有chunks
     * 
     * <p>租户隔离：只删除 tenant_id = principal.tenantId() 且 doc_id = docId 的记录。
     * 
     * @param principal 租户主体
     * @param docId 文档ID
     * @return 删除的行数
     */
    int deleteChunksByDocument(TenantPrincipal principal, UUID docId);
}
