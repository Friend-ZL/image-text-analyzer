package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.dto.AnalysisResponse;
import com.portfolio.analyzer.exception.InvalidFileTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalysisService.class);
    
    private final ImageProcessingService imageProcessingService;
    private final MultimodalAIClient aiClient;
    
    public DocumentAnalysisService(ImageProcessingService imageProcessingService, 
                                  MultimodalAIClient aiClient) {
        this.imageProcessingService = imageProcessingService;
        this.aiClient = aiClient;
    }
    
    /**
     * 分析文档（图片或PDF）并回答问题
     * 
     * @param file 上传的文件
     * @param question 用户问题
     * @return 分析结果
     */
    public AnalysisResponse analyzeDocument(MultipartFile file, String question) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        
        logger.info("Analyzing document: {}, size: {} bytes, question: {}", 
            file.getOriginalFilename(), file.getSize(), question);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 处理文件获取图片
            List<byte[]> images;
            String fileType;
            
            if (imageProcessingService.isImageFile(file.getContentType())) {
                images = imageProcessingService.processImage(file);
                fileType = "image";
                logger.info("Processed as image file");
            } else if (imageProcessingService.isPdfFile(file.getContentType())) {
                images = imageProcessingService.processPdf(file);
                fileType = "pdf";
                logger.info("Processed as PDF file with {} pages", images.size());
            } else {
                throw new InvalidFileTypeException(
                    "不支持的文件类型: " + file.getContentType() + 
                    "。支持的类型: 图片(JPEG, PNG, GIF, WebP) 和 PDF");
            }
            
            // 调用AI分析
            String answer = aiClient.analyzeImages(images, question);
            
            long latencyMs = System.currentTimeMillis() - startTime;
            
            // 构建响应
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", file.getOriginalFilename());
            metadata.put("fileType", fileType);
            metadata.put("fileSize", file.getSize());
            metadata.put("contentType", file.getContentType());
            
            if ("pdf".equals(fileType)) {
                metadata.put("pageCount", images.size());
            }
            
            AnalysisResponse response = new AnalysisResponse(
                answer,
                aiClient.getModelName(),
                latencyMs,
                metadata
            );
            
            logger.info("Analysis completed in {}ms", latencyMs);
            return response;
            
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.error("Failed to analyze document after {}ms", latencyMs, e);
            throw e;
        }
    }
}
