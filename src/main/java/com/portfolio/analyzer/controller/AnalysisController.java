package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.common.ApiResponse;
import com.portfolio.analyzer.common.ErrorCode;
import com.portfolio.analyzer.dto.AnalysisResponse;
import com.portfolio.analyzer.exception.BusinessException;
import com.portfolio.analyzer.service.DocumentAnalysisService;
import com.portfolio.analyzer.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Validated
public class AnalysisController {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisController.class);
    
    private final DocumentAnalysisService analysisService;
    
    public AnalysisController(DocumentAnalysisService analysisService) {
        this.analysisService = analysisService;
    }
    
    /**
     * 分析文档接口 - v1（当前版本）
     * 
     * @param file 上传的图片或PDF文件
     * @param question 用户问题
     * @return 统一响应体包装的分析结果
     */
    @PostMapping(value = "/v1/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AnalysisResponse>> analyzeV1(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") String question) {
        
        validateParameters(file, question);
        
        logger.info("Received analysis request (v1): file={}, question={}", 
            LogSanitizer.sanitizeFile(file), LogSanitizer.sanitizeQuestion(question));
        
        AnalysisResponse data = analysisService.analyzeDocument(file, question);
        String traceId = MDC.get("traceId");
        
        ApiResponse<AnalysisResponse> response = ApiResponse.success(data, traceId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 分析文档接口 - 已废弃（保留兼容）
     * 
     * @deprecated 请使用 /api/v1/analyze
     * @param file 上传的图片或PDF文件
     * @param question 用户问题
     * @return 统一响应体包装的分析结果
     */
    @Deprecated
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AnalysisResponse>> analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") String question) {
        
        validateParameters(file, question);
        
        logger.warn("Using deprecated API: /api/analyze, please migrate to /api/v1/analyze");
        
        AnalysisResponse data = analysisService.analyzeDocument(file, question);
        String traceId = MDC.get("traceId");
        
        ApiResponse<AnalysisResponse> response = ApiResponse.success(data, traceId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.add("Deprecation", "true");
        headers.add("Sunset", "Thu, 31 Dec 2026 23:59:59 GMT");
        headers.add("Link", "</api/v1/analyze>; rel=\"successor-version\"");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        String traceId = MDC.get("traceId");
        return ResponseEntity.ok(ApiResponse.success("OK", traceId));
    }
    
    private void validateParameters(MultipartFile file, String question) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.QUESTION_EMPTY);
        }
    }
}
