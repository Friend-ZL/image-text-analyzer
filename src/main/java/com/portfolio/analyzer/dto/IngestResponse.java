package com.portfolio.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {
    private String message;
    private int chunksProcessed;
    private long processingTimeMs;
}
