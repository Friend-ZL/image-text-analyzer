package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.rerank.DashScopeReranker;
import com.portfolio.analyzer.rerank.NoopReranker;
import com.portfolio.analyzer.rerank.RerankFailedException;
import com.portfolio.analyzer.rerank.RerankerPort;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.util.PromptInjectionSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG查询服务
 * 
 * <p>实现检索增强生成，支持多租户隔离和提示注入防护。
 * 租户/ACL过滤在数据库SQL WHERE子句中执行，在 LIMIT 之前。
 * 重排只处理这一批已经过滤过的候选。
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
    private final NoopReranker noopReranker;
    private final DashScopeReranker dashScopeReranker;
    private final Counter fallbackCounter;

    /**
     * 供未接入重排的调用方和既有单测使用。重排视为关闭。
     */
    public RagQueryService(
            VectorStorePort vectorStore,
            EmbeddingModel embeddingModel,
            MultimodalAIClient aiClient,
            RagConfig ragConfig) {
        this(vectorStore, embeddingModel, aiClient, ragConfig,
                new NoopReranker(), null, new SimpleMeterRegistry());
    }

    @Autowired
    public RagQueryService(
            VectorStorePort vectorStore,
            EmbeddingModel embeddingModel,
            MultimodalAIClient aiClient,
            RagConfig ragConfig,
            NoopReranker noopReranker,
            DashScopeReranker dashScopeReranker,
            MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.aiClient = aiClient;
        this.ragConfig = ragConfig;
        this.noopReranker = noopReranker;
        this.dashScopeReranker = dashScopeReranker;
        MeterRegistry registry = meterRegistry == null ? new SimpleMeterRegistry() : meterRegistry;
        this.fallbackCounter = Counter.builder("rag.rerank.fallback")
                .description("RAG rerank calls that fell back to vector similarity order")
                .register(registry);
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
        
        boolean rerankEnabled = isRerankEnabled();
        int topK = ragConfig.getTopK();
        int limit = rerankEnabled ? recallLimit(topK) : topK;

        // 通过VectorStorePort进行向量搜索（数据库级别tenant/ACL过滤，发生在 LIMIT 之前）
        List<VectorStorePort.SearchResult> searchResults = vectorStore.searchSimilar(
                principal,
                queryEmbedding,
                limit,
                ragConfig.getSimilarityThreshold()
        );
        
        logger.info("Found {} accessible documents after tenant/ACL filtering", searchResults.size());
        
        if (searchResults.isEmpty()) {
            logger.info("No relevant documents found for tenant {}", principal.tenantId());
            return buildEmptyResponse(startTime, false);
        }

        Ranked ranked = rank(question, searchResults, topK, rerankEnabled);
        List<VectorStorePort.SearchResult> selected = ranked.results();
        if (selected.isEmpty()) {
            logger.info("No documents left after rerank for tenant {}", principal.tenantId());
            return buildEmptyResponse(startTime, ranked.reranked());
        }
        
        // 收集文档内容，构建安全prompt
        List<String> docContents = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            VectorStorePort.SearchResult result = selected.get(i);
            String content = result.content();
            Integer pageNum = result.pageNo();
            String pageInfo = pageNum != null ? " (第" + pageNum + "页)" : "";
            docContents.add("[数据块 " + (i + 1) + "]" + pageInfo + ": " + content);
        }
        
        String prompt = PromptInjectionSanitizer.buildSecurePrompt(question, docContents);
        
        logger.debug("Calling LLM with prompt length: {}", prompt.length());
        String answer = aiClient.chat(prompt);
        
        List<RagQueryResponse.DocumentSource> sources = new ArrayList<>();
        for (VectorStorePort.SearchResult result : selected) {
            sources.add(buildDocumentSource(result));
        }
        
        long latencyMs = System.currentTimeMillis() - startTime;
        
        logger.info("RAG query completed in {}ms with {} sources, reranked={}",
                latencyMs, sources.size(), ranked.reranked());
        
        return new RagQueryResponse(
                answer,
                sources,
                ragConfig.getChatModel(),
                latencyMs,
                ranked.reranked()
        );
    }

    /**
     * 关闭重排时按向量相似度取 topK。打开时先重排再按 min-score 截断。
     * 超时、5xx 或解析失败回退到 Noop，并计数 rag.rerank.fallback。
     */
    private Ranked rank(
            String question,
            List<VectorStorePort.SearchResult> recalled,
            int topK,
            boolean rerankEnabled) {
        if (!rerankEnabled) {
            return new Ranked(unwrap(noopReranker.rerank(question, recalled, topK)), false);
        }
        RagConfig.Rerank settings = ragConfig.getRerank();
        String provider = settings.getProvider();
        boolean dashscope = provider == null || provider.isBlank() || "dashscope".equalsIgnoreCase(provider);
        if (!dashscope || dashScopeReranker == null) {
            logger.warn("Rerank enabled but provider is unavailable, using vector order: provider={}", provider);
            fallbackCounter.increment();
            return new Ranked(unwrap(noopReranker.rerank(question, recalled, topK)), false);
        }
        try {
            List<RerankerPort.Scored> scored = dashScopeReranker.rerank(question, recalled, topK);
            return new Ranked(aboveMinScore(scored), true);
        } catch (RerankFailedException ex) {
            logger.warn("Rerank failed, falling back to vector similarity: {}", ex.toString());
            fallbackCounter.increment();
            return new Ranked(unwrap(noopReranker.rerank(question, recalled, topK)), false);
        }
    }

    private List<VectorStorePort.SearchResult> aboveMinScore(List<RerankerPort.Scored> scored) {
        double minScore = ragConfig.getRerank().getMinScore();
        List<VectorStorePort.SearchResult> kept = new ArrayList<>();
        for (RerankerPort.Scored hit : scored) {
            if (hit.score() >= minScore) {
                kept.add(hit.result());
                logger.info("Rerank kept chunk {} score={}", hit.result().id(), hit.score());
            } else {
                logger.info("Rerank dropped chunk {} score={} minScore={}",
                        hit.result().id(), hit.score(), minScore);
            }
        }
        return kept;
    }

    private static List<VectorStorePort.SearchResult> unwrap(List<RerankerPort.Scored> scored) {
        List<VectorStorePort.SearchResult> results = new ArrayList<>(scored.size());
        for (RerankerPort.Scored hit : scored) {
            results.add(hit.result());
        }
        return results;
    }

    private boolean isRerankEnabled() {
        RagConfig.Rerank rerank = ragConfig.getRerank();
        return rerank != null && rerank.isEnabled();
    }

    private int recallLimit(int topK) {
        int recall = ragConfig.getRecallTopN();
        if (recall < topK) {
            return topK;
        }
        return recall;
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
        
        String key = "\"" + fieldName + "\"";
        int keyAt = json.indexOf(key);
        if (keyAt < 0) {
            return null;
        }
        int cursor = keyAt + key.length();
        while (cursor < json.length() && Character.isWhitespace(json.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= json.length() || json.charAt(cursor) != ':') {
            return null;
        }
        cursor++;
        while (cursor < json.length() && Character.isWhitespace(json.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= json.length() || json.charAt(cursor) != '"') {
            return null;
        }
        int start = cursor + 1;
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }
    
    /**
     * 构建空响应
     */
    private RagQueryResponse buildEmptyResponse(long startTime, boolean reranked) {
        long latencyMs = System.currentTimeMillis() - startTime;
        return new RagQueryResponse(
                "抱歉，我在知识库中没有找到相关信息来回答您的问题。",
                List.of(),
                ragConfig.getChatModel(),
                latencyMs,
                reranked
        );
    }

    private record Ranked(List<VectorStorePort.SearchResult> results, boolean reranked) {}
}
