package com.portfolio.analyzer.controller;

import com.portfolio.analyzer.dto.AnalysisResponse;
import com.portfolio.analyzer.exception.InvalidFileTypeException;
import com.portfolio.analyzer.service.DocumentAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private DocumentAnalysisService analysisService;
    
    @Test
    void testAnalyze_ValidRequest_ShouldReturnSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "test.jpg");
        metadata.put("fileType", "image");
        
        AnalysisResponse response = new AnalysisResponse(
            "这是一张测试图片",
            "qwen-vl-max",
            1000L,
            metadata
        );
        
        when(analysisService.analyzeDocument(any(), anyString())).thenReturn(response);
        
        mockMvc.perform(multipart("/api/analyze")
                .file(file)
                .param("question", "这是什么?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("这是一张测试图片"))
            .andExpect(jsonPath("$.model").value("qwen-vl-max"))
            .andExpect(jsonPath("$.latencyMs").value(1000))
            .andExpect(jsonPath("$.metadata.fileName").value("test.jpg"));
    }
    
    @Test
    void testAnalyze_EmptyFile_ShouldReturnBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[0]
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new IllegalArgumentException("文件不能为空"));
        
        mockMvc.perform(multipart("/api/analyze")
                .file(file)
                .param("question", "这是什么?"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("参数错误"));
    }
    
    @Test
    void testAnalyze_EmptyQuestion_ShouldReturnBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new IllegalArgumentException("问题不能为空"));
        
        mockMvc.perform(multipart("/api/analyze")
                .file(file)
                .param("question", ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("参数错误"));
    }
    
    @Test
    void testAnalyze_InvalidFileType_ShouldReturnBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", new byte[]{1, 2, 3}
        );
        
        when(analysisService.analyzeDocument(any(), anyString()))
            .thenThrow(new InvalidFileTypeException("不支持的文件类型"));
        
        mockMvc.perform(multipart("/api/analyze")
                .file(file)
                .param("question", "分析这个文件"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("无效的文件类型"));
    }
}
