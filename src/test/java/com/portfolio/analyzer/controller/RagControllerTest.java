package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.dto.IngestResponse;
import com.portfolio.analyzer.dto.RagQueryRequest;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RagControllerTest {

    @Mock
    private DocumentIngestionService ingestionService;

    @Mock
    private RagQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RagController controller = new RagController(ingestionService, queryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testIngestDocument_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Test content".getBytes()
        );

        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("documentId", "doc-123");
        serviceResult.put("pagesProcessed", 1);
        serviceResult.put("chunksProcessed", 3);
        serviceResult.put("processingTimeMs", 100L);

        when(ingestionService.ingestDocument(any(), anyString())).thenReturn(serviceResult);

        mockMvc.perform(multipart("/api/rag/ingest")
                        .file(file)
                        .param("source", "test-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-123"))
                .andExpect(jsonPath("$.pagesProcessed").value(1))
                .andExpect(jsonPath("$.chunksProcessed").value(3))
                .andExpect(jsonPath("$.processingTimeMs").value(100));
    }

    @Test
    void testIngestDocument_WithoutSource() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Test content".getBytes()
        );

        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("documentId", "doc-123");
        serviceResult.put("pagesProcessed", 1);
        serviceResult.put("chunksProcessed", 3);
        serviceResult.put("processingTimeMs", 100L);

        when(ingestionService.ingestDocument(any(), anyString())).thenReturn(serviceResult);

        mockMvc.perform(multipart("/api/rag/ingest")
                        .file(file))
                .andExpect(status().isOk());
    }

    @Test
    void testQueryWithRag_Success() throws Exception {
        RagQueryResponse response = new RagQueryResponse(
                "This is the answer.",
                new ArrayList<>(),
                "qwen-plus",
                150L
        );

        when(queryService.queryWithContext(anyString())).thenReturn(response);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is Spring AI?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("This is the answer."))
                .andExpect(jsonPath("$.model").value("qwen-plus"))
                .andExpect(jsonPath("$.latencyMs").value(150));
    }

    @Test
    void testQueryWithRag_EmptyQuestion() throws Exception {
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testQueryWithRag_NullQuestion() throws Exception {
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testQueryWithRag_QuestionTooLong() throws Exception {
        String longQuestion = "a".repeat(2001);
        
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + longQuestion + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
