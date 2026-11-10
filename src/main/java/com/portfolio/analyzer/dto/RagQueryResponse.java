package com.portfolio.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class RagQueryResponse {
    private String answer;
    private List<DocumentSource> sources;
    private String model;
    private long latencyMs;

    /**
     * 本次是否采用了重排序结果。关闭或降级为 false；重排调用成功为 true，
     * 即使随后被 min-score 全部截掉也仍为 true。
     */
    private boolean reranked;

    public RagQueryResponse(String answer, List<DocumentSource> sources, String model, long latencyMs) {
        this(answer, sources, model, latencyMs, false);
    }

    public RagQueryResponse(String answer, List<DocumentSource> sources, String model, long latencyMs, boolean reranked) {
        this.answer = answer;
        this.sources = sources;
        this.model = model;
        this.latencyMs = latencyMs;
        this.reranked = reranked;
    }
    
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

