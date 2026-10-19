package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.common.ErrorCode;
import com.portfolio.analyzer.dto.AnalysisResponse;
import com.portfolio.analyzer.service.DocumentAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 容器层 MaxUploadSizeExceededException 集成测试
 * 验证 MaxUploadSizeExceededException handler 优先级高于 MultipartException handler
 * 注意：MockMvc 无法触发真实的容器级大小限制，此测试通过 service 抛出异常来验证 handler 行为
 * 真实的容器级限制需要通过 REST Assured 或真实 HTTP 客户端测试
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MaxUploadSizeIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private DocumentAnalysisService analysisService;
    
    @Test
    void testMaxUploadSizeExceeded_ShouldReturn413NotCaughtByMultipartHandler() throws Exception {
        // 模拟容器抛出 MaxUploadSizeExceededException（它是 MultipartException 的子类）
        // 验证它被专门的 handler 拦截返回 413，而不是被 MultipartException handler 拦截返回 400
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[1024]
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new MaxUploadSizeExceededException(10 * 1024 * 1024));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value(ErrorCode.FILE_TOO_LARGE.getCode()));
    }
}
