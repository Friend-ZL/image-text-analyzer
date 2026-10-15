package com.portfolio.analyzer.dto;

import java.util.Map;

public class AnalysisResponse {
    
    private String answer;
    private String model;
    private long latencyMs;
    private Map<String, Object> metadata;
    
    public AnalysisResponse() {
    }
    
    public AnalysisResponse(String answer, String model, long latencyMs, Map<String, Object> metadata) {
        this.answer = answer;
        this.model = model;
        this.latencyMs = latencyMs;
        this.metadata = metadata;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public long getLatencyMs() {
        return latencyMs;
    }
    
    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
