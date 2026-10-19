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
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RAG查询服务
 * 
 * <p>实现检索增强生成，支持多租户隔离和提示注入防护。
 * 租户/ACL过滤在SQL WHERE子句中执行，在topK之前。
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
        
        // 构建SQL WHERE过滤条件：tenant_id = ? AND (owner_user_id = ? OR acl_groups ?| userGroups)
        Filter.Expression tenantFilter = buildTenantAclFilter(tenantId, userId, userGroups);
        
        SearchRequest searchRequest = SearchRequest.query(question)
                .withTopK(ragConfig.getTopK())
                .withSimilarityThreshold(ragConfig.getSimilarityThreshold())
                .withFilterExpression(tenantFilter);
        
        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);
        
        logger.info("Found {} accessible documents after tenant/ACL filtering", relevantDocs.size());
        
        if (relevantDocs.isEmpty()) {
            logger.info("No relevant documents found for tenant {}", tenantId);
            return buildEmptyResponse(startTime);
        }
        
        // 收集文档内容，构建安全prompt
        List<String> docContents = new ArrayList<>();
        for (int i = 0; i < relevantDocs.size(); i++) {
            Document doc = relevantDocs.get(i);
            String content = doc.getContent();
            Integer pageNum = (Integer) doc.getMetadata().get("page_number");
            String pageInfo = pageNum != null ? " (第" + pageNum + "页)" : "";
            docContents.add("[数据块 " + (i + 1) + "]" + pageInfo + ": " + content);
        }
        
        String prompt = PromptInjectionSanitizer.buildSecurePrompt(question, docContents);
        
        logger.debug("Calling LLM with prompt length: {}", prompt.length());
        String answer = aiClient.chat(prompt);
        
        List<RagQueryResponse.DocumentSource> sources = new ArrayList<>();
        for (Document doc : relevantDocs) {
            sources.add(buildDocumentSource(doc));
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
     * 构建租户和ACL过滤表达式
     * 
     * SQL: WHERE metadata->>'tenant_id' = tenantId 
     *      AND (metadata->>'owner_user_id' = userId 
     *           OR metadata->'acl_groups' ?| ARRAY['group1', 'group2'])
     */
    private Filter.Expression buildTenantAclFilter(String tenantId, String userId, List<String> userGroups) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        
        // tenant_id = tenantId (必须)
        var tenantExpr = b.eq("tenant_id", tenantId);
        
        // ACL检查: owner_user_id = userId OR acl_groups contains any of userGroups
        FilterExpressionBuilder.Op aclExpr;
        if (userId != null && userGroups != null && !userGroups.isEmpty()) {
            // (owner_user_id = userId OR acl_groups IN userGroups)
            var ownerExpr = b.eq("owner_user_id", userId);
            var groupsExpr = b.in("acl_groups", userGroups.toArray());
            aclExpr = b.or(ownerExpr, groupsExpr);
        } else if (userId != null) {
            // 只有userId，没有groups
            aclExpr = b.eq("owner_user_id", userId);
        } else if (userGroups != null && !userGroups.isEmpty()) {
            // 只有groups，没有userId
            aclExpr = b.in("acl_groups", userGroups.toArray());
        } else {
            // 既没有userId也没有groups，只能看到公开文档
            // 不设置ACL限制，只过滤tenant
            return b.and(tenantExpr, b.eq("owner_user_id", userId != null ? userId : "")).build();
        }
        
        // tenant_id = ? AND (owner_user_id = ? OR acl_groups IN (?))
        return b.and(tenantExpr, aclExpr).build();
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
        
        Object pageNumObj = doc.getMetadata().get("page_number");
        Integer pageNumber = pageNumObj instanceof Number 
                ? ((Number) pageNumObj).intValue() 
                : null;
        
        return new RagQueryResponse.DocumentSource(
                excerpt,
                (String) doc.getMetadata().get("source"),
                (String) doc.getMetadata().get("file_name"),
                pageNumber,
                similarity.orElse(null),
                (String) doc.getMetadata().get("content_type")
        );
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
        return Optional.empty();
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

