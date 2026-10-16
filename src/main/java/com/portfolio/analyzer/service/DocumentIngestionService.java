package com.portfolio.analyzer.service;

import com.portfolio.analyzer.config.RagConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIngestionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);
    
    private final VectorStore vectorStore;
    private final RagConfig ragConfig;
    
    public DocumentIngestionService(VectorStore vectorStore, RagConfig ragConfig) {
        this.vectorStore = vectorStore;
        this.ragConfig = ragConfig;
    }
    
    /**
     * 将文档内容切块并存入向量数据库
     * 
     * @param file 上传的文件
     * @param source 文档来源标识
     * @return 处理的文档块数量
     * @throws IOException 文件读取异常
     */
    public int ingestDocument(MultipartFile file, String source) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        logger.info("Starting document ingestion: file={}, source={}", 
                file.getOriginalFilename(), source);
        
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        
        if (content.trim().isEmpty()) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", source);
        metadata.put("fileName", file.getOriginalFilename());
        metadata.put("contentType", file.getContentType());
        metadata.put("fileSize", file.getSize());
        
        Document document = new Document(content, metadata);
        
        TokenTextSplitter splitter = new TokenTextSplitter(
                ragConfig.getChunkSize(),
                ragConfig.getChunkOverlap(),
                5,
                10000,
                true
        );
        
        List<Document> chunks = splitter.split(document);
        
        logger.info("Split document into {} chunks", chunks.size());
        
        vectorStore.add(chunks);
        
        logger.info("Successfully ingested {} chunks from {}", chunks.size(), source);
        
        return chunks.size();
    }
}
