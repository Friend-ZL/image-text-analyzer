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
}
