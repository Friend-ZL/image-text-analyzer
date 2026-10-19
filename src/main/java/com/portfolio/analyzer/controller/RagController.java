package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.dto.RagQueryRequest;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.security.TenantContext;
import com.portfolio.analyzer.security.TenantPrincipal;
import com.portfolio.analyzer.service.DocumentIngestionService;
import com.portfolio.analyzer.service.RagQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * RAG控制器
 * 
 * <p>提供文档入库和RAG查询的HTTP API。
 * 在entry层解析TenantPrincipal一次，然后传递给服务层。
 * 
 * @since Stage 2 - VectorStore port refactor
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {
    
    private final TenantContext tenantContext;
    private final DocumentIngestionService ingestionService;
    private final RagQueryService queryService;
    
    public RagController(
            TenantContext tenantContext,
            DocumentIngestionService ingestionService,
            RagQueryService queryService) {
        this.tenantContext = tenantContext;
        this.ingestionService = ingestionService;
        this.queryService = queryService;
    }
    
    /**
     * 入库文档
     * 
     * @param file 上传的文件
     * @param source 文档来源（可选）
     * @return 入库结果
     */
    @PostMapping("/ingest")
    public ResponseEntity<?> ingestDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", required = false, defaultValue = "upload") String source) {
        
        try {
            // 在entry层解析TenantPrincipal一次
            TenantPrincipal principal = tenantContext.current();
            
            Map<String, Object> result = ingestionService.ingestDocument(principal, file, source);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ingestion failed", "message", e.getMessage()));
        }
    }
    
    /**
     * RAG查询
     * 
     * @param request 查询请求（包含问题）
     * @return 查询响应（包含答案和来源）
     */
    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@RequestBody RagQueryRequest request) {
        // 在entry层解析TenantPrincipal一次
        TenantPrincipal principal = tenantContext.current();
        
        RagQueryResponse response = queryService.queryWithContext(principal, request.getQuestion());
        return ResponseEntity.ok(response);
    }
}
