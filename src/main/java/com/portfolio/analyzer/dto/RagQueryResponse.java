package com.portfolio.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryResponse {
    private String answer;
    private List<DocumentSource> sources;
    private String model;
    private long latencyMs;
    
    /**
     * 文档来源（白名单字段）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSource {
        /**
         * 文档片段内容（摘录）
         */
        private String excerpt;
        
        /**
         * 文档来源标识
         */
        private String source;
        
        /**
         * 文件名
         */
        private String fileName;
        
        /**
         * 页码
         */
        private Integer pageNumber;
        
        /**
         * 相似度分数（0-1）
         */
        private Double similarity;
        
        /**
         * 内容类型
         */
        private String contentType;
    }
}

