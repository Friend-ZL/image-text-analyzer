package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.dto.IngestResponse;
import com.portfolio.analyzer.dto.RagQueryRequest;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/rag")
@Validated
public class RagController {
    
    private static final Logger logger = LoggerFactory.getLogger(RagController.class);
    
    private final DocumentIngestionService ingestionService;
    private final RagQueryService queryService;
    
    public RagController(DocumentIngestionService ingestionService, 
                        RagQueryService queryService) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
    }
    
    /**
     * 文档入库接口
     * 
     * @param file 要入库的文档文件
     * @param source 文档来源标识
     * @return 入库结果
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingestDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("source") String source) throws IOException {
        
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        if (source == null || source.trim().isEmpty()) {
            source = file.getOriginalFilename();
        }
        
        logger.info("Received ingest request: file={}, source={}", 
                file.getOriginalFilename(), source);
        
        long startTime = System.currentTimeMillis();
        int chunksProcessed = ingestionService.ingestDocument(file, source);
        long processingTime = System.currentTimeMillis() - startTime;
        
        IngestResponse response = new IngestResponse(
                "文档入库成功",
                chunksProcessed,
                processingTime
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * RAG检索增强问答接口
     * 
     * @param request 查询请求（包含问题和可选的会话ID）
     * @return 答案和相关来源
     */
    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> queryWithRag(
            @Valid @RequestBody RagQueryRequest request) {
        
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        
        logger.info("Received RAG query: question={}, conversationId={}", 
                request.getQuestion(), request.getConversationId());
        
        RagQueryResponse response = queryService.queryWithContext(request.getQuestion());
        
        return ResponseEntity.ok(response);
    }
}
