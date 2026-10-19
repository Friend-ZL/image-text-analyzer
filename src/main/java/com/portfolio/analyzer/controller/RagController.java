package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.dto.IngestResponse;
import com.portfolio.analyzer.dto.RagQueryRequest;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * RAG API 控制器
 * 
 * <p>提供文档入库和检索增强问答API。
 * 
 * @since Stage 2
 */
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
            @RequestParam(value = "source", required = false) @Size(max = 128) String source) 
            throws IOException {
        
        if (source == null || source.trim().isEmpty()) {
            source = file.getOriginalFilename();
        }
        
        logger.info("Received ingest request: file={}, size={}, source={}", 
                file.getOriginalFilename(), file.getSize(), source);
        
        Map<String, Object> result = ingestionService.ingestDocument(file, source);
        
        IngestResponse response = new IngestResponse(
                (String) result.get("documentId"),
                (Integer) result.get("pagesProcessed"),
                (Integer) result.get("chunksProcessed"),
                (Long) result.get("processingTimeMs")
        );
        
        logger.info("Ingest completed: documentId={}, pages={}, chunks={}, time={}ms",
                response.getDocumentId(),
                response.getPagesProcessed(),
                response.getChunksProcessed(),
                response.getProcessingTimeMs());
        
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
        
        logger.info("Received RAG query: questionLength={}, conversationId={}", 
                request.getQuestion().length(), request.getConversationId());
        
        RagQueryResponse response = queryService.queryWithContext(request.getQuestion());
        
        logger.info("RAG query completed: sources={}, latency={}ms",
                response.getSources().size(), response.getLatencyMs());
        
        return ResponseEntity.ok(response);
    }
}

