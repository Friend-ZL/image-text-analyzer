package com.portfolio.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文档页面
 * 
 * <p>表示文档的一页内容，可能来自文本提取或VLM OCR。
 * 
 * @since Stage 2 Refactor
 */
@Data
@AllArgsConstructor
public class DocumentPage {
    /**
     * 页码（从1开始）
     */
    private int pageNumber;
    
    /**
     * 页面内容
     */
    private String content;
    
    /**
     * 内容来源类型
     */
    private SourceType sourceType;
}
