package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.security.TenantPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {
    
    @Mock
    private VectorStorePort vectorStore;
    
    @Mock
    private EmbeddingModel embeddingModel;
    
    @Mock
    private MultimodalAIClient aiClient;
    
    @Mock
    private RagConfig ragConfig;
    
    private RagQueryService ragQueryService;
    private TenantPrincipal testPrincipal;
    
    @BeforeEach
    void setUp() {
        lenient().when(ragConfig.getTopK()).thenReturn(3);
        lenient().when(ragConfig.getSimilarityThreshold()).thenReturn(0.7);
        lenient().when(ragConfig.getChatModel()).thenReturn("qwen-plus");
        lenient().when(ragConfig.getEmbeddingDimensions()).thenReturn(1024);
        
        ragQueryService = new RagQueryService(vectorStore, embeddingModel, aiClient, ragConfig);
        testPrincipal = new TenantPrincipal("test-tenant", "test-user", Set.of(), Set.of("group1"));
    }
    
    @Test
    void testQueryWithContext_Success() {
        String question = "What is Spring AI?";
        float[] queryEmbedding = new float[1024];
        
        VectorStorePort.SearchResult result1 = new VectorStorePort.SearchResult(
                UUID.randomUUID(),
                "Spring AI is a powerful framework.",
                1,
                "text",
                0.2,
                "{\"file_name\": \"doc1.txt\", \"source\": \"doc1.txt\"}"
        );
        
        when(embeddingModel.embed(anyString())).thenReturn(queryEmbedding);
        when(vectorStore.searchSimilar(any(TenantPrincipal.class), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(result1));
        when(aiClient.chat(anyString())).thenReturn("Spring AI is a framework for building AI applications.");
        
        RagQueryResponse response = ragQueryService.queryWithContext(testPrincipal, question);
        
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isNotBlank();
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getModel()).isEqualTo("qwen-plus");
    }
    
    @Test
    void testQueryWithContext_NoResults() {
        String question = "unknown query";
        float[] queryEmbedding = new float[1024];
        
        when(embeddingModel.embed(anyString())).thenReturn(queryEmbedding);
        when(vectorStore.searchSimilar(any(TenantPrincipal.class), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(Collections.emptyList());
        
        RagQueryResponse response = ragQueryService.queryWithContext(testPrincipal, question);
        
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).contains("没有找到相关信息");
        assertThat(response.getSources()).isEmpty();
    }
    
    @Test
    void testQueryWithContext_NullQuestion() {
        assertThatThrownBy(() -> ragQueryService.queryWithContext(testPrincipal, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("问题不能为空");
    }
    
    @Test
    void testQueryWithContext_EmptyQuestion() {
        assertThatThrownBy(() -> ragQueryService.queryWithContext(testPrincipal, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("问题不能为空");
    }
    
    @Test
    void testQueryWithContext_ExtractsSimilarityCorrectly() {
        String question = "Test question";
        float[] queryEmbedding = new float[1024];
        
        VectorStorePort.SearchResult result = new VectorStorePort.SearchResult(
                UUID.randomUUID(),
                "Test content.",
                1,
                "text",
                0.3, // distance
                "{\"file_name\": \"doc.txt\", \"source\": \"doc.txt\"}"
        );
        
        when(embeddingModel.embed(anyString())).thenReturn(queryEmbedding);
        when(vectorStore.searchSimilar(any(TenantPrincipal.class), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(result));
        when(aiClient.chat(anyString())).thenReturn("Answer");
        
        RagQueryResponse response = ragQueryService.queryWithContext(testPrincipal, question);
        
        assertThat(response.getSources()).hasSize(1);
        RagQueryResponse.DocumentSource source = response.getSources().get(0);
        assertThat(source.getSimilarity()).isEqualTo(0.7); // 1 - 0.3
    }
    
    @Test
    void testQueryWithContext_MultipleResults() {
        String question = "multiple results";
        float[] queryEmbedding = new float[1024];
        
        VectorStorePort.SearchResult result1 = new VectorStorePort.SearchResult(
                UUID.randomUUID(), "Content 1", 1, "text", 0.1,
                "{\"file_name\": \"doc1.txt\", \"source\": \"s1\"}"
        );
        VectorStorePort.SearchResult result2 = new VectorStorePort.SearchResult(
                UUID.randomUUID(), "Content 2", 2, "text", 0.2,
                "{\"file_name\": \"doc2.txt\", \"source\": \"s2\"}"
        );
        
        when(embeddingModel.embed(anyString())).thenReturn(queryEmbedding);
        when(vectorStore.searchSimilar(any(TenantPrincipal.class), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(result1, result2));
        when(aiClient.chat(anyString())).thenReturn("Answer");
        
        RagQueryResponse response = ragQueryService.queryWithContext(testPrincipal, question);
        
        assertThat(response.getSources()).hasSize(2);
    }
}
