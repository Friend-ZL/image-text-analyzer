package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.common.ErrorCode;
import com.portfolio.analyzer.dto.AnalysisResponse;
import com.portfolio.analyzer.exception.AIServiceException;
import com.portfolio.analyzer.exception.FileSizeLimitException;
import com.portfolio.analyzer.exception.InvalidFileTypeException;
import com.portfolio.analyzer.exception.ModelTimeoutException;
import com.portfolio.analyzer.service.DocumentAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private DocumentAnalysisService analysisService;
    
    @Test
    void testAnalyzeV1_ValidRequest_ShouldReturnSuccessWithTraceId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "test.jpg");
        metadata.put("fileType", "image");
        
        AnalysisResponse response = new AnalysisResponse(
            "这是一张测试图片",
            "qwen-vl-max",
            1000L,
            metadata
        );
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "这是什么?"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.message").value("成功"))
            .andExpect(jsonPath("$.data.answer").value("这是一张测试图片"))
            .andExpect(jsonPath("$.data.model").value("qwen-vl-max"))
            .andExpect(jsonPath("$.data.latencyMs").value(1000))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testAnalyzeV1_WithCustomTraceId_ShouldPreserveIt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        AnalysisResponse response = new AnalysisResponse("answer", "model", 1000L, metadata);
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        String customTraceId = "custom-trace-id-12345";
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("X-Request-Id", customTraceId))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Trace-Id", customTraceId))
            .andExpect(jsonPath("$.traceId").value(customTraceId));
    }
    
    @Test
    void testAnalyze_DeprecatedEndpoint_ShouldReturnDeprecationHeaders() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        AnalysisResponse response = new AnalysisResponse("answer", "model", 1000L, metadata);
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        mockMvc.perform(multipart("/api/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Deprecation"))
            .andExpect(header().string("Deprecation", "true"))
            .andExpect(header().exists("Sunset"))
            .andExpect(header().exists("Link"))
            .andExpect(header().string("Link", "</api/v1/analyze>; rel=\"successor-version\""))
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));
    }
    
    @Test
    void testAnalyzeV1_EmptyFile_ShouldReturnBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[0]
        );
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "这是什么?"))
            .andExpect(status().isBadRequest())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.getCode()))
            .andExpect(jsonPath("$.message").value("文件不能为空"))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testAnalyzeV1_EmptyQuestion_ShouldReturnBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", ""))
            .andExpect(status().isBadRequest())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.getCode()))
            .andExpect(jsonPath("$.message").value("问题不能为空"))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testAnalyzeV1_InvalidFileType_ShouldReturnUnsupportedMediaType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", new byte[]{1, 2, 3}
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new InvalidFileTypeException("不支持的文件类型: text/plain"));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "分析这个文件"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_FILE_TYPE.getCode()))
            .andExpect(jsonPath("$.message").value("不支持的文件类型: text/plain"))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testAnalyzeV1_FileTooLarge_ShouldReturnPayloadTooLarge() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "large.jpg", "image/jpeg", new byte[1024]
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new FileSizeLimitException("文件大小超过10MB限制"));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.FILE_TOO_LARGE.getCode()))
            .andExpect(jsonPath("$.message").value("文件大小超过10MB限制"))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testAnalyzeV1_AIServiceError_ShouldReturnInternalError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new AIServiceException("模型服务暂时不可用"));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isInternalServerError())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.AI_SERVICE_ERROR.getCode()))
            .andExpect(jsonPath("$.message").value("模型服务暂时不可用"))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testAnalyzeV1_ModelTimeout_ShouldReturnGatewayTimeout() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new ModelTimeoutException("模型调用超时"));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.MODEL_TIMEOUT.getCode()))
            .andExpect(jsonPath("$.message").value("模型调用超时"))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testAnalyzeV1_AIServiceWithTimeoutCause_ShouldReturnGatewayTimeout() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        TimeoutException timeoutException = new TimeoutException("Connection timeout");
        AIServiceException aiException = new AIServiceException("AI调用失败", timeoutException);
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(aiException);
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.MODEL_TIMEOUT.getCode()))
            .andExpect(jsonPath("$.message").value("模型调用超时，请稍后重试"))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testAnalyzeV1_UnexpectedException_ShouldReturnInternalError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new RuntimeException("Unexpected error"));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isInternalServerError())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
            .andExpect(jsonPath("$.message").value("处理请求时发生错误"))
            .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
    
    @Test
    void testHealth_ShouldReturnOK() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data").value("OK"));
    }
}
