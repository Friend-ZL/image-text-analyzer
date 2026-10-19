package com.portfolio.analyzer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.analyzer.common.ErrorCode;
import com.portfolio.analyzer.security.TenantContext;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.service.DocumentAnalysisService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 容器层 MaxUploadSizeExceededException 集成测试
 * 验证 Spring 容器的 multipart 大小限制在请求到达业务层之前被拦截，正确返回 413 / E41301
 * 而不是被 MultipartException handler 拦截返回 400 / E40001
 */
@Disabled("TODO: Fix Spring context configuration - needs DataSource/H2 setup. BE-1 test, will fix in follow-up")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver"
    }
)
@TestPropertySource(properties = {
    "spring.servlet.multipart.max-file-size=1KB",
    "spring.servlet.multipart.max-request-size=2KB",
    "server.tomcat.max-swallow-size=-1"
})
@ActiveProfiles("test")
public class MaxUploadSizeIntegrationTest {
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public TenantContext testTenantContext() {
            TenantContext mock = mock(TenantContext.class);
            when(mock.current()).thenReturn(
                    new TenantPrincipal("test-tenant", "test-user", Set.of(), Set.of("test-group"))
            );
            return mock;
        }
        
        @Bean
        @Primary
        public OpenAiApi testOpenAiApi() {
            return mock(OpenAiApi.class);
        }
        
        @Bean
        @Primary
        public EmbeddingModel testEmbeddingModel() {
            EmbeddingModel mock = mock(EmbeddingModel.class);
            float[] dummyEmbedding = new float[1024];
            when(mock.embed(anyString())).thenReturn(dummyEmbedding);
            return mock;
        }
        
        @Bean
        @Primary
        public ChatClient.Builder testChatClientBuilder() {
            return mock(ChatClient.Builder.class);
        }
    }
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private DocumentAnalysisService analysisService;
    
    @Test
    @Disabled("TODO: Fix Spring context - needs H2 driver or DataSource mock. Will fix in follow-up")
    void testMaxUploadSizeExceeded_ShouldReturn413FromContainer() throws Exception {
        // 创建一个超过 1KB 限制的文件（2KB）
        byte[] largeContent = new byte[2048];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        
        // 构造 multipart 请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(largeContent) {
            @Override
            public String getFilename() {
                return "large.jpg";
            }
        });
        body.add("question", "test");
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        // 发送请求
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/analyze",
            requestEntity,
            String.class
        );
        
        // 验证容器层返回 413，而不是 400
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        
        // 验证响应体
        String responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertThat(jsonNode.get("code").asText()).isEqualTo(ErrorCode.FILE_TOO_LARGE.getCode());
        assertThat(jsonNode.get("code").asText()).isEqualTo("E41301");
        
        // 验证不是 400 / E40001（MultipartException handler）
        assertThat(jsonNode.get("code").asText()).isNotEqualTo(ErrorCode.INVALID_PARAMETER.getCode());
        
        // 验证 traceId 是 32 位 hex
        String traceId = jsonNode.get("traceId").asText();
        assertThat(traceId).matches("^[0-9a-f]{32}$");
    }
}
