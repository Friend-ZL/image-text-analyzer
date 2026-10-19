package com.portfolio.analyzer.service;

import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.DocumentChunk;
import com.portfolio.analyzer.dto.DocumentPage;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.security.TenantPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Array;
import java.util.*;

/**
 * 文档入库服务
 * 
 * <p>实现文档的提取、切块、向量化和存储，支持多租户隔离。
 * 所有chunk存储通过VectorStorePort进行，确保安全边界。
 * 
 * @since Stage 2 - VectorStore port refactor
 */
@Service
public class DocumentIngestionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);
    
    private final VectorStorePort vectorStore;
    private final EmbeddingModel embeddingModel;
    private final RagConfig ragConfig;
    private final DocumentExtractionService extractionService;
    private final DocumentChunkingService chunkingService;
    private final JdbcTemplate jdbcTemplate;
    
    public DocumentIngestionService(
            VectorStorePort vectorStore,
            EmbeddingModel embeddingModel,
            RagConfig ragConfig,
            DocumentExtractionService extractionService,
            DocumentChunkingService chunkingService,
            JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.ragConfig = ragConfig;
        this.extractionService = extractionService;
        this.chunkingService = chunkingService;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 入库文档（幂等操作）
     * 
     * <p>基于 UNIQUE (tenant_id, doc_id, chunk_index) 实现幂等性。
     * 
     * @param principal 租户主体
     * @param file 上传的文件
     * @param source 文档来源标识
     * @return 文档ID和处理结果
     * @throws IOException IO异常
     */
    @Transactional
    public Map<String, Object> ingestDocument(TenantPrincipal principal, MultipartFile file, String source) 
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        String documentId = UUID.randomUUID().toString();
        
        logger.info("Starting document ingestion: documentId={}, file={}, source={}, tenant={}, user={}, fileSize={}", 
                documentId, file.getOriginalFilename(), source, principal.tenantId(), 
                principal.userId(), file.getSize());
        
        long startTime = System.currentTimeMillis();
        
        // 提取和切块
        List<DocumentPage> pages = extractionService.extractPages(file);
        logger.info("Extracted {} pages from document {}", pages.size(), documentId);
        
        List<DocumentChunk> chunks = chunkingService.chunkPages(pages);
        logger.info("Created {} chunks from document {}", chunks.size(), documentId);
        
        // 保存文档元数据
        saveDocumentMetadata(principal, documentId, file, source, pages.size(), chunks.size());
        
        // 生成embeddings并构建ChunkData
        List<VectorStorePort.ChunkData> chunkDataList = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            
            // 调用EmbeddingModel生成embedding（确保dimensions=1024）
            float[] embedding = embeddingModel.embed(chunk.getContent());
            
            // 验证维度
            if (embedding.length != ragConfig.getEmbeddingDimensions()) {
                throw new IllegalStateException(
                        "Embedding dimension mismatch: expected " + ragConfig.getEmbeddingDimensions() +
                        ", got " + embedding.length);
            }
            
            // 构建metadata JSONB（仅非安全字段）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("file_name", file.getOriginalFilename());
            metadata.put("content_type", file.getContentType());
            metadata.put("source", source);
            String metadataJson = toJsonString(metadata);
            
            chunkDataList.add(new VectorStorePort.ChunkData(
                    UUID.fromString(documentId),
                    chunk.getPageNumber(),
                    i, // chunk_index
                    chunk.getSourceType().name().toLowerCase(),
                    chunk.getContent(),
                    ragConfig.getEmbeddingModel(),
                    ragConfig.getEmbeddingDimensions(),
                    embedding,
                    metadataJson
            ));
        }
        
        // 通过VectorStorePort存储（幂等）
        int affected = vectorStore.upsertChunks(principal, chunkDataList);
        logger.info("Successfully upserted {} chunks for document {}", affected, documentId);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("documentId", documentId);
        result.put("pagesProcessed", pages.size());
        result.put("chunksProcessed", chunks.size());
        result.put("processingTimeMs", processingTime);
        
        return result;
    }
    
    /**
     * 保存文档元数据
     */
    private void saveDocumentMetadata(
            TenantPrincipal principal,
            String documentId,
            MultipartFile file,
            String source,
            int pageCount,
            int totalChunks) {
        
        String sql = "INSERT INTO document " +
                "(document_id, tenant_id, owner_user_id, acl_groups, file_name, content_type, " +
                "file_size, source, page_count, total_chunks, embedding_model, embedding_dim) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        String[] groupsArray = principal.groups().toArray(new String[0]);
        
        jdbcTemplate.update(sql,
                UUID.fromString(documentId),
                principal.tenantId(),
                principal.userId(),
                groupsArray,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                source,
                pageCount,
                totalChunks,
                ragConfig.getEmbeddingModel(),
                ragConfig.getEmbeddingDimensions()
        );
        
        logger.debug("Saved document metadata: documentId={}", documentId);
    }
    
    /**
     * 删除文档（用于实现幂等）
     */
    @Transactional
    public void deleteDocument(TenantPrincipal principal, String documentId) {
        // 删除chunks（通过VectorStorePort）
        vectorStore.deleteChunksByDocument(principal, UUID.fromString(documentId));
        
        // 删除document元数据
        String sql = "DELETE FROM document WHERE document_id = ? AND tenant_id = ?";
        int deleted = jdbcTemplate.update(sql, UUID.fromString(documentId), principal.tenantId());
        
        if (deleted > 0) {
            logger.info("Deleted document: documentId={}, tenant={}", documentId, principal.tenantId());
        }
    }
    
    /**
     * 简单JSON序列化（仅用于metadata中的简单字段）
     */
    private String toJsonString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }
    
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
