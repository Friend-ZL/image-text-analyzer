package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.common.ErrorCode;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.security.TenantContext;
import com.portfolio.analyzer.security.TenantMissingException;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
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

@WebMvcTest(
        controllers = RagController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
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
        
        mockMvc.perform(multipart("/api/v1/rag/ingest")
                        .file(file)
                        .param("source", "test-source")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-User-Id", "user-1")
                        .header("X-Roles", "user")
                        .header("X-Groups", "group-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.documentId").value("doc-123"));
    }
    
    @Test
    void testIngestDocument_MissingTenant() throws Exception {
        when(tenantContext.current()).thenThrow(new TenantMissingException("Tenant ID required"));
        
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test".getBytes()
        );
        
        mockMvc.perform(multipart("/api/v1/rag/ingest")
                        .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_MISSING.getCode()))
                .andExpect(jsonPath("$.code").value("E40102"));
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
        
        mockMvc.perform(post("/api/v1/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-User-Id", "user-1")
                        .header("X-Roles", "user")
                        .header("X-Groups", "group-a")
                        .content("{\"question\": \"What is Spring AI?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.answer").value("Answer to the question"))
                .andExpect(jsonPath("$.data.model").value("qwen-plus"));
    }
    
    @Test
    void testQuery_MissingTenant() throws Exception {
        when(tenantContext.current()).thenThrow(new TenantMissingException("Missing X-Tenant-Id header"));
        
        mockMvc.perform(post("/api/v1/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_MISSING.getCode()))
                .andExpect(jsonPath("$.code").value("E40102"));
    }
    
    @Test
    void testQuery_InvalidQuestion() throws Exception {
        TenantPrincipal principal = new TenantPrincipal("tenant-1", "user-1", Set.of(), Set.of());
        
        when(tenantContext.current()).thenReturn(principal);
        when(queryService.queryWithContext(any(TenantPrincipal.class), anyString()))
                .thenThrow(new IllegalArgumentException("问题不能为空"));
        
        mockMvc.perform(post("/api/v1/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "tenant-1")
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E40001"));
    }
}
