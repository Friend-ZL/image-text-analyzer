package com.portfolio.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestRequest {
    private String content;
    private String source;
    private String metadata;
}
