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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
@ExtendWith(OutputCaptureExtension.class)
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
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_WithValidTraceparent_ShouldExtractTraceId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        AnalysisResponse response = new AnalysisResponse("answer", "model", 1000L, metadata);
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        String expectedTraceId = "0af7651916cd43dd8448eb211c80319c";
        String traceparent = "00-" + expectedTraceId + "-b7ad6b7169203331-01";
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("traceparent", traceparent))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Trace-Id", expectedTraceId))
            .andExpect(jsonPath("$.traceId").value(expectedTraceId));
    }
    
    @Test
    void testAnalyzeV1_WithValidXRequestId_ShouldUseIt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        AnalysisResponse response = new AnalysisResponse("answer", "model", 1000L, metadata);
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        String requestId = "abc123def456";
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("X-Request-Id", requestId))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_WithInvalidXRequestId_ShouldRegenerate() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        AnalysisResponse response = new AnalysisResponse("answer", "model", 1000L, metadata);
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        // 测试超长 ID
        String longId = "a".repeat(65);
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("X-Request-Id", longId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
        
        // 测试包含非法字符（换行符）
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("X-Request-Id", "test\r\nMalicious-Header: value"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
        
        // 测试包含空格
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("X-Request-Id", "test id with spaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_WithAllZeroTraceId_ShouldRegenerate() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        AnalysisResponse response = new AnalysisResponse("answer", "model", 1000L, metadata);
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        String invalidTraceparent = "00-00000000000000000000000000000000-b7ad6b7169203331-01";
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("traceparent", invalidTraceparent))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.traceId").value(not("00000000000000000000000000000000")))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_MDCAndLogging_ShouldContainTraceId(CapturedOutput output) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        AnalysisResponse response = new AnalysisResponse("answer", "model", 1000L, metadata);
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isOk());
        
        // 验证日志中包含 traceId（格式：[traceId=xxx requestId=]）
        assertThat(output.getOut()).containsPattern("\\[traceId=[0-9a-f]{32}");
        assertThat(output.getOut()).contains("Request:");
        assertThat(output.getOut()).contains("Response:");
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
    void testAnalyzeV1_EmptyFile_ShouldReturnE40002() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[0]
        );
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "这是什么?"))
            .andExpect(status().isBadRequest())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.FILE_EMPTY.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.FILE_EMPTY.getMessage()))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_EmptyQuestion_ShouldReturnE40003() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", ""))
            .andExpect(status().isBadRequest())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.QUESTION_EMPTY.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.QUESTION_EMPTY.getMessage()))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_MissingFileParameter_ShouldReturnE40001() throws Exception {
        mockMvc.perform(multipart("/api/v1/analyze")
                .param("question", "test"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_PARAMETER.getMessage()));
    }
    
    @Test
    void testAnalyzeV1_MissingQuestionParameter_ShouldReturnE40001() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_PARAMETER.getMessage()));
    }
    
    @Test
    void testAnalyzeV1_InvalidFileType_ShouldReturnE41501() throws Exception {
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
            .andExpect(jsonPath("$.message").value(ErrorCode.UNSUPPORTED_FILE_TYPE.getMessage()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_FileTooLarge_ShouldReturnE41301() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "large.jpg", "image/jpeg", new byte[1024]
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new FileSizeLimitException("文件大小超过限制"));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.FILE_TOO_LARGE.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.FILE_TOO_LARGE.getMessage()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_AIServiceError_ShouldNotLeakUpstreamMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        String upstreamErrorMessage = "Internal DashScope error: API key invalid, detailed stack trace...";
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new AIServiceException("模型服务暂时不可用", new RuntimeException(upstreamErrorMessage)));
        
        String responseBody = mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isBadGateway())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.MODEL_ERROR.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.MODEL_ERROR.getMessage()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        // 验证响应中不包含上游错误信息
        assertThat(responseBody).doesNotContain("DashScope");
        assertThat(responseBody).doesNotContain("API key invalid");
        assertThat(responseBody).doesNotContain("stack trace");
    }
    
    @Test
    void testAnalyzeV1_ModelTimeout_ShouldReturnE50401() throws Exception {
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
            .andExpect(jsonPath("$.message").value(ErrorCode.MODEL_TIMEOUT.getMessage()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_AIServiceWithDirectTimeout_ShouldReturnE50401() throws Exception {
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
            .andExpect(jsonPath("$.message").value(ErrorCode.MODEL_TIMEOUT.getMessage()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_AIServiceWithNestedTimeout_ShouldReturnE50401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        // 测试嵌套两层的超时异常
        SocketTimeoutException socketTimeout = new SocketTimeoutException("Read timed out");
        RuntimeException wrapper1 = new RuntimeException("Wrapper 1", socketTimeout);
        RuntimeException wrapper2 = new RuntimeException("Wrapper 2", wrapper1);
        AIServiceException aiException = new AIServiceException("AI调用失败", wrapper2);
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(aiException);
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.MODEL_TIMEOUT.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.MODEL_TIMEOUT.getMessage()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_CustomTimeoutException_ShouldReturnE50201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        // 自定义超时异常（类名包含 Timeout 但不是明确的超时类型）
        class CustomConnectionTimeout extends RuntimeException {
            public CustomConnectionTimeout(String message) {
                super(message);
            }
        }
        
        CustomConnectionTimeout customTimeout = new CustomConnectionTimeout("Custom timeout");
        AIServiceException aiException = new AIServiceException("AI调用失败", customTimeout);
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(aiException);
        
        // 应该返回 E50201（模型服务异常），而不是 E50401（模型超时）
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value(ErrorCode.MODEL_ERROR.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.MODEL_ERROR.getMessage()));
    }
    
    @Test
    void testAnalyzeV1_JavaUtilTimeoutException_ShouldReturnE50401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        // java.util.concurrent.TimeoutException
        TimeoutException timeoutEx = new TimeoutException("Operation timed out");
        AIServiceException aiException = new AIServiceException("AI调用失败", timeoutEx);
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(aiException);
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.code").value(ErrorCode.MODEL_TIMEOUT.getCode()));
    }
    
    @Test
    void testAnalyzeV1_SocketTimeoutException_ShouldReturnE50401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        // java.net.SocketTimeoutException
        SocketTimeoutException socketTimeout = new SocketTimeoutException("Read timed out");
        AIServiceException aiException = new AIServiceException("AI调用失败", socketTimeout);
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(aiException);
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.code").value(ErrorCode.MODEL_TIMEOUT.getCode()));
    }
    
    @Test
    void testAnalyzeV1_UnexpectedException_ShouldReturnE50001() throws Exception {
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
            .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getMessage()))
            .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-f]{32}$")));
    }
    
    @Test
    void testAnalyzeV1_MethodNotAllowed_ShouldReturnE40501WithAllowHeader() throws Exception {
        mockMvc.perform(get("/api/v1/analyze"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("Allow"))
            .andExpect(header().string("Allow", containsString("POST")))
            .andExpect(jsonPath("$.code").value(ErrorCode.METHOD_NOT_ALLOWED.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.METHOD_NOT_ALLOWED.getMessage()));
    }
    
    @Test
    void testNotFoundResource_ShouldReturnE40401() throws Exception {
        mockMvc.perform(get("/api/v1/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND.getMessage()));
    }
    
    @Test
    void testHealth_ShouldReturnOK() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
            .andExpect(jsonPath("$.data").value("OK"));
    }
    
    @Test
    void testAnalyzeV1_HttpMediaTypeNotSupported_ShouldReturn415() throws Exception {
        // Content-Type 不是 multipart/form-data（触发 HttpMediaTypeNotSupportedException）
        mockMvc.perform(post("/api/v1/analyze")
                .contentType("application/json")
                .content("{\"question\":\"test\"}"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_FILE_TYPE.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.UNSUPPORTED_FILE_TYPE.getMessage()));
    }
    
    @Test
    void testAnalyze_HttpMediaTypeNotSupported_ShouldReturn415() throws Exception {
        // 旧接口 - Content-Type 不对
        mockMvc.perform(post("/api/analyze")
                .contentType("application/x-www-form-urlencoded")
                .param("question", "test"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_FILE_TYPE.getCode()));
    }
    
    @Test
    void testAnalyzeV1_MultipartParsingError_ShouldReturn400() throws Exception {
        // multipart 解析失败（模拟损坏的请求体）
        // 注：实际 MultipartException 很难在 MockMvc 中模拟，这里验证 handler 存在
        // 真实场景：请求 Content-Type 是 multipart 但 body 格式错误
        mockMvc.perform(post("/api/v1/analyze")
                .contentType("multipart/form-data; boundary=----invalid")
                .content("----invalid\r\nbroken content"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.getCode()));
    }
    
    @Test
    void testAnalyzeV1_MaxUploadSizeExceeded_ShouldReturn413() throws Exception {
        // MaxUploadSizeExceededException 是 MultipartException 子类，必须返回 413 而不是 400
        // 注：MockMvc 无法真实触发容器级别的大小限制，此测试验证 handler 优先级
        MockMultipartFile hugeFile = new MockMultipartFile(
            "file", "huge.jpg", "image/jpeg", new byte[1024]
        );
        
        // 实际场景中，超过 spring.servlet.multipart.max-file-size 会抛 MaxUploadSizeExceededException
        // 这里通过 service 层模拟（在真实环境中应使用集成测试）
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new MaxUploadSizeExceededException(10 * 1024 * 1024));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(hugeFile)
                .param("question", "test"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value(ErrorCode.FILE_TOO_LARGE.getCode()));
    }
    
    @Test
    void testAnalyzeV1_MaliciousContentType_ShouldBeSanitizedInLog(CapturedOutput output) throws Exception {
        // 恶意 Content-Type（包含换行符和控制字符，尝试日志注入）
        String maliciousContentType = "text/plain\r\n[INJECTED] Fake log entry";
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", maliciousContentType, new byte[]{1, 2, 3}
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new InvalidFileTypeException("不支持的图片类型: [other]"));
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test"))
            .andExpect(status().isUnsupportedMediaType());
        
        // 验证日志中不包含恶意 Content-Type 原文
        String logOutput = output.getOut();
        assertThat(logOutput).doesNotContain("INJECTED");
        assertThat(logOutput).doesNotContain("Fake log entry");
        // 验证使用了白名单清洗后的值
        assertThat(logOutput).contains("[other]");
    }
    
    @Test
    void testAnalyzeV1_UUIDRequestId_ShouldUseAsTraceId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        AnalysisResponse mockResponse = new AnalysisResponse("Test result", "qwen-vl-max", 100L, null);
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenReturn(mockResponse);
        
        String uuidRequestId = "550e8400-e29b-41d4-a716-446655440000";
        String expectedTraceId = "550e8400e29b41d4a716446655440000";
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("X-Request-Id", uuidRequestId))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Trace-Id", expectedTraceId))
            .andExpect(header().string("X-Request-Id", uuidRequestId));
    }
    
    @Test
    void testAnalyzeV1_NonHexRequestId_ShouldGenerateTraceIdAndEchoRequestId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        AnalysisResponse mockResponse = new AnalysisResponse("Test result", "qwen-vl-max", 100L, null);
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenReturn(mockResponse);
        
        String customRequestId = "my-custom-request-id-12345";
        
        mockMvc.perform(multipart("/api/v1/analyze")
                .file(file)
                .param("question", "test")
                .header("X-Request-Id", customRequestId))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Trace-Id", matchesPattern("^[0-9a-f]{32}$")))
            .andExpect(header().string("X-Request-Id", customRequestId))
            .andDo(result -> {
                String traceId = result.getResponse().getHeader("X-Trace-Id");
                String requestId = result.getResponse().getHeader("X-Request-Id");
                // traceId 不等于 requestId，且 traceId 非全零
                assertThat(traceId).isNotEqualTo(requestId);
                assertThat(traceId).doesNotMatch("^0+$");
            });
    }
}
