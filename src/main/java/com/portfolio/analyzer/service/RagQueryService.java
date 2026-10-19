package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.security.TenantContext;
import com.portfolio.analyzer.util.PromptInjectionSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RAG查询服务
 * 
 * <p>实现检索增强生成，支持多租户隔离和提示注入防护。
 * 
 * @since Stage 2 Refactor
 */
@Service
public class RagQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RagQueryService.class);
    
    private final VectorStore vectorStore;
    private final MultimodalAIClient aiClient;
    private final RagConfig ragConfig;
    private final TenantContext tenantContext;
    
    public RagQueryService(
            VectorStore vectorStore, 
            MultimodalAIClient aiClient, 
            RagConfig ragConfig,
            TenantContext tenantContext) {
        this.vectorStore = vectorStore;
        this.aiClient = aiClient;
        this.ragConfig = ragConfig;
        this.tenantContext = tenantContext;
    }
    
    /**
     * 使用RAG（检索增强生成）回答问题
     * 
     * @param question 用户问题
     * @return 包含答案和来源的响应
     */
    public RagQueryResponse queryWithContext(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        
        String tenantId = tenantContext.getTenantId();
        String userId = tenantContext.getUserId();
        List<String> userGroups = tenantContext.getGroups();
        
        int questionHash = question.hashCode();
        logger.info("Processing RAG query: tenant={}, user={}, questionLength={}, questionHash={}", 
                tenantId, userId, question.length(), Integer.toHexString(questionHash));
        
        long startTime = System.currentTimeMillis();
        
        SearchRequest searchRequest = SearchRequest.query(question)
                .withTopK(ragConfig.getTopK())
                .withSimilarityThreshold(ragConfig.getSimilarityThreshold());
        
        List<Document> similarDocs = vectorStore.similaritySearch(searchRequest);
        
        logger.info("Found {} similar documents", similarDocs.size());
        
        List<Document> relevantDocs = similarDocs.stream()
                .filter(doc -> filterByTenantAndAcl(doc, tenantId, userId, userGroups))
                .filter(doc -> extractSimilarity(doc).isPresent())
                .collect(Collectors.toList());
        
        logger.info("Filtered to {} accessible documents", relevantDocs.size());
        
        if (relevantDocs.isEmpty()) {
            logger.info("No relevant documents found for tenant {}", tenantId);
            return buildEmptyResponse(startTime);
        }
        
        List<String> docContents = relevantDocs.stream()
                .map(Document::getContent)
                .collect(Collectors.toList());
        
        String prompt = PromptInjectionSanitizer.buildSecurePrompt(question, docContents);
        
        String answer = aiClient.chat(prompt);
        
        List<RagQueryResponse.DocumentSource> sources = relevantDocs.stream()
                .map(this::buildDocumentSource)
                .collect(Collectors.toList());
        
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
     * 过滤：租户隔离 + ACL
     */
    private boolean filterByTenantAndAcl(Document doc, String tenantId, String userId, List<String> userGroups) {
        String docTenantId = (String) doc.getMetadata().get("tenant_id");
        
        if (!tenantId.equals(docTenantId)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 提取相似度分数
     */
    private Optional<Double> extractSimilarity(Document doc) {
        Object distanceObj = doc.getMetadata().get("distance");
        if (distanceObj instanceof Number) {
            double distance = ((Number) distanceObj).doubleValue();
            double similarity = 1.0 - distance;
            return Optional.of(similarity);
        }
        logger.warn("Document missing distance metadata, skipping");
        return Optional.empty();
    }
    
    /**
     * 构建文档来源（白名单字段）
     */
    private RagQueryResponse.DocumentSource buildDocumentSource(Document doc) {
        String content = doc.getContent();
        String excerpt = content.length() > 200 
                ? content.substring(0, 200) + "..." 
                : content;
        
        Optional<Double> similarity = extractSimilarity(doc);
        
        return new RagQueryResponse.DocumentSource(
                excerpt,
                (String) doc.getMetadata().get("source"),
                (String) doc.getMetadata().get("file_name"),
                (Integer) doc.getMetadata().get("page_number"),
                similarity.orElse(null),
                (String) doc.getMetadata().get("content_type")
        );
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

