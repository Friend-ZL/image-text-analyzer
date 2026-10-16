package com.portfolio.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryResponse {
    private String answer;
    private List<DocumentSource> sources;
    private String model;
    private long latencyMs;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSource {
        private String content;
        private String source;
        private double similarity;
        private Map<String, Object> metadata;
    }
}
