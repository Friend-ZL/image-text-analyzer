package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.RagQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RagQueryService.class);
    
    private final VectorStore vectorStore;
    private final MultimodalAIClient aiClient;
    private final RagConfig ragConfig;
    
    public RagQueryService(VectorStore vectorStore, 
                          MultimodalAIClient aiClient, 
                          RagConfig ragConfig) {
        this.vectorStore = vectorStore;
        this.aiClient = aiClient;
        this.ragConfig = ragConfig;
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
        
        logger.info("Processing RAG query: {}", question);
        
        long startTime = System.currentTimeMillis();
        
        SearchRequest searchRequest = SearchRequest.query(question)
                .withTopK(ragConfig.getTopK());
        
        List<Document> similarDocs = vectorStore.similaritySearch(searchRequest);
        
        logger.info("Found {} similar documents", similarDocs.size());
        
        double threshold = ragConfig.getSimilarityThreshold();
        List<Document> relevantDocs = similarDocs.stream()
                .filter(doc -> {
                    Object distanceObj = doc.getMetadata().get("distance");
                    if (distanceObj instanceof Number) {
                        double distance = ((Number) distanceObj).doubleValue();
                        double similarity = 1.0 - distance;
                        return similarity >= threshold;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        
        logger.info("Filtered to {} relevant documents (threshold: {})", 
                relevantDocs.size(), threshold);
        
        String context = relevantDocs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));
        
        String prompt = buildPrompt(question, context);
        
        String answer = aiClient.chat(prompt);
        
        List<RagQueryResponse.DocumentSource> sources = relevantDocs.stream()
                .map(doc -> {
                    Object distanceObj = doc.getMetadata().get("distance");
                    double distance = distanceObj instanceof Number 
                            ? ((Number) distanceObj).doubleValue() 
                            : 0.0;
                    double similarity = 1.0 - distance;
                    
                    return new RagQueryResponse.DocumentSource(
                            doc.getContent(),
                            (String) doc.getMetadata().get("source"),
                            similarity,
                            doc.getMetadata()
                    );
                })
                .collect(Collectors.toList());
        
        long latencyMs = System.currentTimeMillis() - startTime;
        
        logger.info("RAG query completed in {}ms with {} sources", latencyMs, sources.size());
        
        return new RagQueryResponse(
                answer,
                sources,
                aiClient.getModelName(),
                latencyMs
        );
    }
    
    /**
     * 构建RAG提示词
     */
    private String buildPrompt(String question, String context) {
        if (context == null || context.trim().isEmpty()) {
            return String.format(
                    "请回答以下问题。如果你不知道答案，请说你不知道，不要编造答案。\n\n问题：%s",
                    question
            );
        }
        
        return String.format(
                "请基于以下上下文信息回答问题。如果上下文中没有相关信息，请说你不知道，不要编造答案。\n\n" +
                "上下文信息：\n%s\n\n" +
                "问题：%s\n\n" +
                "答案：",
                context,
                question
        );
    }
}
