package com.portfolio.analyzer.service;

import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.DocumentChunk;
import com.portfolio.analyzer.dto.DocumentPage;
import com.portfolio.analyzer.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 文档入库服务
 * 
 * <p>实现文档的提取、切块、向量化和存储，支持多租户隔离。
 * 
 * @since Stage 2 Refactor
 */
@Service
public class DocumentIngestionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final String EMBEDDING_MODEL = "text-embedding-v4";
    private static final int EMBEDDING_DIM = 1024;
    
    private final VectorStore vectorStore;
    private final RagConfig ragConfig;
    private final TenantContext tenantContext;
    private final DocumentExtractionService extractionService;
    private final DocumentChunkingService chunkingService;
    private final JdbcTemplate jdbcTemplate;
    
    public DocumentIngestionService(
            VectorStore vectorStore,
            RagConfig ragConfig,
            TenantContext tenantContext,
            DocumentExtractionService extractionService,
            DocumentChunkingService chunkingService,
            JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.ragConfig = ragConfig;
        this.tenantContext = tenantContext;
        this.extractionService = extractionService;
        this.chunkingService = chunkingService;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 入库文档（幂等操作）
     * 
     * @param file 上传的文件
     * @param source 文档来源标识
     * @return 文档ID和处理结果
     * @throws IOException IO异常
     */
    @Transactional
    public Map<String, Object> ingestDocument(MultipartFile file, String source) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        String tenantId = tenantContext.getTenantId();
        String userId = tenantContext.getUserId();
        List<String> groups = tenantContext.getGroups();
        
        String documentId = UUID.randomUUID().toString();
        
        logger.info("Starting document ingestion: documentId={}, file={}, source={}, tenant={}, user={}, fileSize={}", 
                documentId, file.getOriginalFilename(), source, tenantId, userId, file.getSize());
        
        long startTime = System.currentTimeMillis();
        
        List<DocumentPage> pages = extractionService.extractPages(file);
        logger.info("Extracted {} pages from document {}", pages.size(), documentId);
        
        List<DocumentChunk> chunks = chunkingService.chunkPages(pages);
        logger.info("Created {} chunks from document {}", chunks.size(), documentId);
        
        saveDocumentMetadata(documentId, tenantId, userId, groups, file, source, pages.size(), chunks.size());
        
        List<Document> vectorDocuments = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("document_id", documentId);
            metadata.put("tenant_id", tenantId);
            metadata.put("page_number", chunk.getPageNumber());
            metadata.put("chunk_index", chunk.getChunkIndex());
            metadata.put("source_type", chunk.getSourceType().name().toLowerCase());
            metadata.put("source", source);
            metadata.put("file_name", file.getOriginalFilename());
            metadata.put("content_type", file.getContentType());
            metadata.put("embedding_model", EMBEDDING_MODEL);
            metadata.put("embedding_dim", EMBEDDING_DIM);
            
            String chunkId = String.format("%s_%d_%d", documentId, chunk.getPageNumber(), chunk.getChunkIndex());
            vectorDocuments.add(new Document(chunkId, chunk.getContent(), metadata));
        }
        
        vectorStore.add(vectorDocuments);
        logger.info("Successfully stored {} chunks for document {}", vectorDocuments.size(), documentId);
        
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
            String documentId,
            String tenantId,
            String userId,
            List<String> groups,
            MultipartFile file,
            String source,
            int pageCount,
            int totalChunks) {
        
        String sql = "INSERT INTO document " +
                "(document_id, tenant_id, owner_user_id, acl_groups, file_name, content_type, " +
                "file_size, source, page_count, total_chunks, embedding_model, embedding_dim) " +
                "VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        String groupsJson = groups != null && !groups.isEmpty() 
                ? "[" + String.join(",", groups.stream().map(g -> "\"" + g + "\"").toList()) + "]"
                : null;
        
        jdbcTemplate.update(sql,
                UUID.fromString(documentId),
                tenantId,
                userId,
                groupsJson,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                source,
                pageCount,
                totalChunks,
                EMBEDDING_MODEL,
                EMBEDDING_DIM
        );
        
        logger.debug("Saved document metadata: documentId={}", documentId);
    }
    
    /**
     * 删除文档（用于实现幂等）
     */
    @Transactional
    public void deleteDocument(String documentId) {
        String tenantId = tenantContext.getTenantId();
        
        String sql = "DELETE FROM document WHERE document_id = ? AND tenant_id = ?";
        int deleted = jdbcTemplate.update(sql, UUID.fromString(documentId), tenantId);
        
        if (deleted > 0) {
            logger.info("Deleted document: documentId={}, tenant={}", documentId, tenantId);
        }
    }
}

