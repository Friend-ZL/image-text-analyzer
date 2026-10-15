package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.dto.AnalysisResponse;
import com.portfolio.analyzer.service.DocumentAnalysisService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * 分析文档接口
     * 
     * @param file 上传的图片或PDF文件
     * @param question 用户问题
     * @return 分析结果
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") String question) {
        
        // 手动验证参数
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        
        logger.info("Received analysis request: file={}, question={}", 
            file.getOriginalFilename(), question);
        
        AnalysisResponse response = analysisService.analyzeDocument(file, question);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
