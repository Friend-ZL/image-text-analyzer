package com.portfolio.analyzer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.portfolio.analyzer.common.ApiResponse;
import com.portfolio.analyzer.service.DocumentAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.ai.openai.api-key=test-key",
    "spring.ai.openai.base-url=http://localhost:8888"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private DocumentAnalysisService analysisService;
    
    @Test
    void shouldReturn401ForNoToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/analyze"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        
        String content = result.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(content, ApiResponse.class);
        
        assertEquals("E40101", response.getCode());
        assertEquals("未认证或令牌无效", response.getMessage());
        assertNull(response.getData());
        
        // 验证 traceId 是 32 位 hex
        String traceId = response.getTraceId();
        assertNotNull(traceId);
        assertTrue(traceId.matches("^[0-9a-f]{32}$"), 
            "traceId should be 32-char hex, but was: " + traceId);
        
        // 验证响应头中的 traceId 一致
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertEquals(traceId, headerTraceId, "Response body traceId should match X-Trace-Id header");
    }
    
    @Test
    void shouldReturn401ForMissingTenantClaim() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/v1/analyze")
                        .with(jwt().jwt(builder -> builder
                                .subject("user1")
                        ))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        )
                .andExpect(status().isUnauthorized())
                .andReturn();
        
        String content = result.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(content, ApiResponse.class);
        
        assertEquals("E40102", response.getCode());
        assertEquals("租户信息缺失", response.getMessage());
        
        // 验证 traceId 是 32 位 hex
        String traceId = response.getTraceId();
        assertNotNull(traceId);
        assertTrue(traceId.matches("^[0-9a-f]{32}$"), 
            "traceId should be 32-char hex, but was: " + traceId);
        
        // 验证响应头中的 traceId 一致
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertEquals(traceId, headerTraceId, "Response body traceId should match X-Trace-Id header");
    }
    
    @Test
    void shouldAllowHealthEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
    
    @Test
    void shouldAllowStaticResourcesWithoutAuth() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }
    
    @Test
    void shouldUseTraceparentTraceId() throws Exception {
        // W3C traceparent 格式：version-trace-id-parent-id-trace-flags
        String expectedTraceId = "0af7651916cd43dd8448eb211c80319c";
        String traceparent = "00-" + expectedTraceId + "-b7ad6b7169203331-01";
        
        MvcResult result = mockMvc.perform(
                get("/api/v1/analyze")
                        .header("traceparent", traceparent)
        )
                .andExpect(status().isUnauthorized())
                .andReturn();
        
        String content = result.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(content, ApiResponse.class);
        
        // 验证使用了 traceparent 中的 trace-id
        assertEquals(expectedTraceId, response.getTraceId(),
            "Should use trace-id from traceparent header");
        
        // 验证响应头一致
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertEquals(expectedTraceId, headerTraceId);
    }
}
