package com.portfolio.analyzer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.rag")
@Getter
@Setter
public class RagConfig {
    
    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private int topK = 3;
    private double similarityThreshold = 0.7;
    private int maxIngestSizeMb = 10;
    private int maxPdfPages = 50;
    private String chatModel = "qwen-plus";
    private String vlmModel = "qwen-vl-max";
    private int minTextLengthForOcr = 50;
    private String embeddingModel = "text-embedding-v4";
    private int embeddingDimensions = 1024;
    
    public long getMaxIngestSizeBytes() {
        return maxIngestSizeMb * 1024L * 1024L;
    }
}
