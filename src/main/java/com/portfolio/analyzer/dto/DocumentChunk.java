package com.portfolio.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文档块
 * 
 * <p>切块后的文档片段，包含元数据。
 * 
 * @since Stage 2 Refactor
 */
@Data
@AllArgsConstructor
public class DocumentChunk {
    /**
     * 页码
     */
    private int pageNumber;
    
    /**
     * 页内块索引（从0开始）
     */
    private int chunkIndex;
    
    /**
     * 块内容
     */
    private String content;
    
    /**
     * 内容来源类型
     */
    private SourceType sourceType;
}
