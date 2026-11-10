package com.portfolio.analyzer.service;

import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.DocumentChunk;
import com.portfolio.analyzer.dto.DocumentPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切块服务
 * 
 * <p>实现基于token的文档切块，支持真正的overlap，切块不跨页。
 * 
 * @since Stage 2 Refactor
 */
@Service
public class DocumentChunkingService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkingService.class);
    
    private final TokenTextSplitter splitter;
    
    public DocumentChunkingService(RagConfig ragConfig) {
        this.splitter = new TokenTextSplitter(
                ragConfig.getChunkSize(),
                ragConfig.getChunkOverlap(),
                10,
                ragConfig.getChunkSize() * 2,
                true
        );
    }
    
    /**
     * 对文档页面进行切块
     * 
     * <p>每页独立切块，不跨页。
     * 
     * @param pages 文档页面列表
     * @return 文档块列表
     */
    public List<DocumentChunk> chunkPages(List<DocumentPage> pages) {
        List<DocumentChunk> allChunks = new ArrayList<>();
        
        for (DocumentPage page : pages) {
            List<DocumentChunk> pageChunks = chunkPage(page);
            allChunks.addAll(pageChunks);
        }
        
        logger.info("Chunked {} pages into {} chunks", pages.size(), allChunks.size());
        return allChunks;
    }
    
    /**
     * 对单页进行切块
     */
    private List<DocumentChunk> chunkPage(DocumentPage page) {
        String content = page.getContent();
        List<DocumentChunk> chunks = new ArrayList<>();
        
        if (content == null || content.trim().isEmpty()) {
            logger.warn("Page {} has no content, skipping", page.getPageNumber());
            return chunks;
        }
        
        Document doc = new Document(content);
        List<Document> splitDocs = splitter.split(doc);
        
        int chunkIndex = 0;
        for (Document splitDoc : splitDocs) {
            String chunkText = splitDoc.getContent().trim();
            if (!chunkText.isEmpty()) {
                chunks.add(new DocumentChunk(
                    page.getPageNumber(),
                    chunkIndex++,
                    chunkText,
                    page.getSourceType()
                ));
            }
        }
        
        logger.debug("Page {} chunked into {} chunks", 
                page.getPageNumber(), chunks.size());
        
        return chunks;
    }
}
