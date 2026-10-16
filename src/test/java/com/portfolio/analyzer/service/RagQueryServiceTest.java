package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.RagQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private MultimodalAIClient aiClient;

    @Mock
    private RagConfig ragConfig;

    private RagQueryService ragQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(ragConfig.getTopK()).thenReturn(3);
        lenient().when(ragConfig.getSimilarityThreshold()).thenReturn(0.7);
        ragQueryService = new RagQueryService(vectorStore, aiClient, ragConfig);
    }

    @Test
    void testQueryWithContext_Success() {
        String question = "What is Spring AI?";
        String expectedAnswer = "Spring AI is a framework for building AI applications.";

        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("source", "doc1.txt");
        Document doc1 = new Document("Spring AI is a powerful framework.", metadata1);
        doc1.getMetadata().put("distance", 0.2);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("source", "doc2.txt");
        Document doc2 = new Document("It helps build AI applications easily.", metadata2);
        doc2.getMetadata().put("distance", 0.3);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Arrays.asList(doc1, doc2));
        when(aiClient.getModelName()).thenReturn("qwen-vl-max");
        when(aiClient.chat(anyString())).thenReturn(expectedAnswer);

        RagQueryResponse response = ragQueryService.queryWithContext(question);

        assertNotNull(response);
        assertEquals(expectedAnswer, response.getAnswer());
        assertEquals("qwen-vl-max", response.getModel());
        assertEquals(2, response.getSources().size());
        assertTrue(response.getLatencyMs() >= 0);
    }

    @Test
    void testQueryWithContext_NoRelevantDocuments() {
        String question = "What is quantum computing?";
        String expectedAnswer = "I don't have enough context to answer this question.";

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Arrays.asList());
        when(aiClient.getModelName()).thenReturn("qwen-vl-max");
        when(aiClient.chat(anyString())).thenReturn(expectedAnswer);

        RagQueryResponse response = ragQueryService.queryWithContext(question);

        assertNotNull(response);
        assertEquals(expectedAnswer, response.getAnswer());
        assertTrue(response.getSources().isEmpty());
    }

    @Test
    void testQueryWithContext_NullQuestion() {
        assertThrows(IllegalArgumentException.class, () -> {
            ragQueryService.queryWithContext(null);
        });
    }

    @Test
    void testQueryWithContext_EmptyQuestion() {
        assertThrows(IllegalArgumentException.class, () -> {
            ragQueryService.queryWithContext("");
        });
    }

    @Test
    void testQueryWithContext_FiltersBelowThreshold() {
        String question = "Test question";

        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("source", "doc1.txt");
        Document doc1 = new Document("Relevant content.", metadata1);
        doc1.getMetadata().put("distance", 0.2);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("source", "doc2.txt");
        Document doc2 = new Document("Not relevant content.", metadata2);
        doc2.getMetadata().put("distance", 0.8);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Arrays.asList(doc1, doc2));
        when(aiClient.getModelName()).thenReturn("qwen-vl-max");
        when(aiClient.chat(anyString())).thenReturn("Answer based on context.");

        RagQueryResponse response = ragQueryService.queryWithContext(question);

        assertNotNull(response);
        assertEquals(1, response.getSources().size());
        assertEquals("doc1.txt", response.getSources().get(0).getSource());
    }

    @Test
    void testQueryWithContext_CalculatesSimilarityCorrectly() {
        String question = "Test question";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "doc.txt");
        Document doc = new Document("Test content.", metadata);
        doc.getMetadata().put("distance", 0.3);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Arrays.asList(doc));
        when(aiClient.getModelName()).thenReturn("qwen-vl-max");
        when(aiClient.chat(anyString())).thenReturn("Answer.");

        RagQueryResponse response = ragQueryService.queryWithContext(question);

        assertNotNull(response);
        assertEquals(1, response.getSources().size());
        double expectedSimilarity = 1.0 - 0.3;
        assertEquals(expectedSimilarity, response.getSources().get(0).getSimilarity(), 0.01);
    }
}
