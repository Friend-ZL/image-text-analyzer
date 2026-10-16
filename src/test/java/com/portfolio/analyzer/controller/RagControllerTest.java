package com.portfolio.analyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.analyzer.dto.IngestResponse;
import com.portfolio.analyzer.dto.RagQueryRequest;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentIngestionService ingestionService;

    @MockBean
    private RagQueryService queryService;

    @Test
    void testIngestDocument_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Test content for ingestion.".getBytes()
        );

        when(ingestionService.ingestDocument(any(), anyString())).thenReturn(5);

        mockMvc.perform(multipart("/api/rag/ingest")
                        .file(file)
                        .param("source", "test-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("文档入库成功"))
                .andExpect(jsonPath("$.chunksProcessed").value(5));
    }

    @Test
    void testIngestDocument_MissingFile() throws Exception {
        mockMvc.perform(multipart("/api/rag/ingest")
                        .param("source", "test-source"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testIngestDocument_EmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/rag/ingest")
                        .file(file)
                        .param("source", "test-source"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testQueryWithRag_Success() throws Exception {
        RagQueryRequest request = new RagQueryRequest("What is Spring AI?", null);
        RagQueryResponse response = new RagQueryResponse(
                "Spring AI is a framework.",
                new ArrayList<>(),
                "qwen-vl-max",
                1000L
        );

        when(queryService.queryWithContext(anyString())).thenReturn(response);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Spring AI is a framework."))
                .andExpect(jsonPath("$.model").value("qwen-vl-max"));
    }

    @Test
    void testQueryWithRag_EmptyQuestion() throws Exception {
        RagQueryRequest request = new RagQueryRequest("", null);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testQueryWithRag_NullQuestion() throws Exception {
        RagQueryRequest request = new RagQueryRequest(null, null);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
