package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.dto.RagQueryRequest;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.security.TenantContext;
import com.portfolio.analyzer.security.TenantMissingException;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
@ActiveProfiles("test")
class RagControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private TenantContext tenantContext;
    
    @MockBean
    private DocumentIngestionService ingestionService;
    
    @MockBean
    private RagQueryService queryService;
    
    @Test
    void testIngestDocument_Success() throws Exception {
        TenantPrincipal principal = new TenantPrincipal("tenant-1", "user-1", Set.of(), Set.of());
        
        when(tenantContext.current()).thenReturn(principal);
        when(ingestionService.ingestDocument(any(TenantPrincipal.class), any(), anyString()))
                .thenReturn(Map.of(
                        "documentId", "doc-123",
                        "pagesProcessed", 5,
                        "chunksProcessed", 10,
                        "processingTimeMs", 1000L
                ));
        
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes()
        );
        
        mockMvc.perform(multipart("/api/rag/ingest")
                        .file(file)
                        .param("source", "test-source")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-123"));
    }
    
    @Test
    void testIngestDocument_MissingTenant() throws Exception {
        when(tenantContext.current()).thenThrow(new TenantMissingException("Tenant ID required"));
        
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test".getBytes()
        );
        
        mockMvc.perform(multipart("/api/rag/ingest")
                        .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TENANT_MISSING"));
    }
    
    @Test
    void testQuery_Success() throws Exception {
        TenantPrincipal principal = new TenantPrincipal("tenant-1", "user-1", Set.of(), Set.of());
        
        RagQueryResponse response = new RagQueryResponse(
                "Answer to the question",
                List.of(),
                "qwen-plus",
                500L
        );
        
        when(tenantContext.current()).thenReturn(principal);
        when(queryService.queryWithContext(any(TenantPrincipal.class), anyString()))
                .thenReturn(response);
        
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-User-Id", "user-1")
                        .content("{\"question\": \"What is Spring AI?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Answer to the question"))
                .andExpect(jsonPath("$.model").value("qwen-plus"));
    }
    
    @Test
    void testQuery_MissingTenant() throws Exception {
        when(tenantContext.current()).thenThrow(new TenantMissingException("Missing X-Tenant-Id header"));
        
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TENANT_MISSING"))
                .andExpect(jsonPath("$.status").value(401));
    }
    
    @Test
    void testQuery_InvalidQuestion() throws Exception {
        TenantPrincipal principal = new TenantPrincipal("tenant-1", "user-1", Set.of(), Set.of());
        
        when(tenantContext.current()).thenReturn(principal);
        when(queryService.queryWithContext(any(TenantPrincipal.class), anyString()))
                .thenThrow(new IllegalArgumentException("问题不能为空"));
        
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "tenant-1")
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
