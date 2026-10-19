package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.util.PromptInjectionSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG查询服务
 * 
 * <p>实现检索增强生成，支持多租户隔离和提示注入防护。
 * 租户/ACL过滤在数据库SQL WHERE子句中执行，在topK之前。
 * 
 * @since Stage 2 - VectorStore port refactor
 */
@Service
public class RagQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RagQueryService.class);
    
    private final VectorStorePort vectorStore;
    private final EmbeddingModel embeddingModel;
    private final MultimodalAIClient aiClient;
    private final RagConfig ragConfig;
    
    public RagQueryService(
            VectorStorePort vectorStore,
            EmbeddingModel embeddingModel,
            MultimodalAIClient aiClient, 
            RagConfig ragConfig) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.aiClient = aiClient;
        this.ragConfig = ragConfig;
    }
    
    /**
     * 使用RAG（检索增强生成）回答问题
     * 
     * @param principal 租户主体
     * @param question 用户问题
     * @return 包含答案和来源的响应
     */
    public RagQueryResponse queryWithContext(TenantPrincipal principal, String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        
        int questionHash = question.hashCode();
        logger.info("Processing RAG query: tenant={}, user={}, questionLength={}, questionHash={}", 
                principal.tenantId(), principal.userId(), question.length(), Integer.toHexString(questionHash));
        
        long startTime = System.currentTimeMillis();
        
        // 生成查询向量
        float[] queryEmbedding = embeddingModel.embed(question);
        
        // 验证维度
        if (queryEmbedding.length != ragConfig.getEmbeddingDimensions()) {
            throw new IllegalStateException(
                    "Query embedding dimension mismatch: expected " + ragConfig.getEmbeddingDimensions() +
                    ", got " + queryEmbedding.length);
        }
        
        // 通过VectorStorePort进行向量搜索（数据库级别tenant/ACL过滤）
        List<VectorStorePort.SearchResult> searchResults = vectorStore.searchSimilar(
                principal,
                queryEmbedding,
                ragConfig.getTopK(),
                ragConfig.getSimilarityThreshold()
        );
        
        logger.info("Found {} accessible documents after tenant/ACL filtering", searchResults.size());
        
        if (searchResults.isEmpty()) {
            logger.info("No relevant documents found for tenant {}", principal.tenantId());
            return buildEmptyResponse(startTime);
        }
        
        // 收集文档内容，构建安全prompt
        List<String> docContents = new ArrayList<>();
        for (int i = 0; i < searchResults.size(); i++) {
            VectorStorePort.SearchResult result = searchResults.get(i);
            String content = result.content();
            Integer pageNum = result.pageNo();
            String pageInfo = pageNum != null ? " (第" + pageNum + "页)" : "";
            docContents.add("[数据块 " + (i + 1) + "]" + pageInfo + ": " + content);
        }
        
        String prompt = PromptInjectionSanitizer.buildSecurePrompt(question, docContents);
        
        logger.debug("Calling LLM with prompt length: {}", prompt.length());
        String answer = aiClient.chat(prompt);
        
        List<RagQueryResponse.DocumentSource> sources = new ArrayList<>();
        for (VectorStorePort.SearchResult result : searchResults) {
            sources.add(buildDocumentSource(result));
        }
        
        long latencyMs = System.currentTimeMillis() - startTime;
        
        logger.info("RAG query completed in {}ms with {} sources", latencyMs, sources.size());
        
        return new RagQueryResponse(
                answer,
                sources,
                ragConfig.getChatModel(),
                latencyMs
        );
    }
    
    /**
     * 构建文档来源（白名单字段）
     */
    private RagQueryResponse.DocumentSource buildDocumentSource(VectorStorePort.SearchResult result) {
        String content = result.content();
        String excerpt = content.length() > 200 
                ? content.substring(0, 200) + "..." 
                : content;
        
        // 从metadata JSONB中提取file_name等信息
        String metadata = result.metadata();
        String fileName = extractJsonField(metadata, "file_name");
        String source = extractJsonField(metadata, "source");
        String contentType = extractJsonField(metadata, "content_type");
        
        return new RagQueryResponse.DocumentSource(
                excerpt,
                source,
                fileName,
                result.pageNo(),
                result.similarity(),
                contentType
        );
    }
    
    /**
     * 从简单JSON中提取字段（metadata是简单结构，不需要完整JSON解析器）
     */
    private String extractJsonField(String json, String fieldName) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        String pattern = "\"" + fieldName + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            return null;
        }
        
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) {
            return null;
        }
        
        return json.substring(start, end);
    }
    
    /**
     * 构建空响应
     */
    private RagQueryResponse buildEmptyResponse(long startTime) {
        long latencyMs = System.currentTimeMillis() - startTime;
        return new RagQueryResponse(
                "抱歉，我在知识库中没有找到相关信息来回答您的问题。",
                List.of(),
                ragConfig.getChatModel(),
                latencyMs
        );
    }
}
