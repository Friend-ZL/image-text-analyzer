package com.portfolio.analyzer.dto;

/**
 * 文档来源类型
 * 
 * @since Stage 2 Refactor
 */
public enum SourceType {
    /**
     * 纯文本提取
     */
    TEXT,
    
    /**
     * VLM OCR（文本层不足时）
     */
    VLM_OCR,
    
    /**
     * 图片描述（独立图片文件）
     */
    IMAGE_DESC
}
