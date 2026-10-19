package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.dto.AnalysisResponse;
import com.portfolio.analyzer.exception.BusinessException;
import com.portfolio.analyzer.exception.InvalidFileTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentAnalysisServiceTest {
    
    @Mock(lenient = true)
    private ImageProcessingService imageProcessingService;
    
    @Mock(lenient = true)
    private MultimodalAIClient aiClient;
    
    @InjectMocks
    private DocumentAnalysisService analysisService;
    
    @BeforeEach
    void setUp() {
        when(aiClient.getModelName()).thenReturn("qwen-vl-max");
    }
    
    @Test
    void testAnalyzeDocument_ImageFile_ShouldReturnResponse() {
        MultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        String question = "这是什么?";
        byte[] imageBytes = new byte[]{1, 2, 3};
        String aiResponse = "这是一张测试图片";
        
        when(imageProcessingService.isImageFile(anyString())).thenReturn(true);
        when(imageProcessingService.isPdfFile(anyString())).thenReturn(false);
        when(imageProcessingService.processImage(any())).thenReturn(List.of(imageBytes));
        when(aiClient.analyzeImages(anyList(), anyString())).thenReturn(aiResponse);
        
        AnalysisResponse response = analysisService.analyzeDocument(file, question);
        
        assertNotNull(response);
        assertEquals(aiResponse, response.getAnswer());
        assertEquals("qwen-vl-max", response.getModel());
        assertTrue(response.getLatencyMs() >= 0);
        assertNotNull(response.getMetadata());
        
        verify(imageProcessingService).processImage(file);
        verify(aiClient).analyzeImages(List.of(imageBytes), question);
    }
    
    @Test
    void testAnalyzeDocument_PdfFile_ShouldReturnResponse() {
        MultipartFile file = new MockMultipartFile(
            "file", "test.pdf", "application/pdf", new byte[]{1, 2, 3}
        );
        String question = "总结这个PDF";
        List<byte[]> pdfImages = List.of(new byte[]{1, 2, 3}, new byte[]{4, 5, 6});
        String aiResponse = "PDF内容总结";
        
        when(imageProcessingService.isImageFile(anyString())).thenReturn(false);
        when(imageProcessingService.isPdfFile(anyString())).thenReturn(true);
        when(imageProcessingService.processPdf(any())).thenReturn(pdfImages);
        when(aiClient.analyzeImages(anyList(), anyString())).thenReturn(aiResponse);
        
        AnalysisResponse response = analysisService.analyzeDocument(file, question);
        
        assertNotNull(response);
        assertEquals(aiResponse, response.getAnswer());
        assertEquals(2, response.getMetadata().get("pageCount"));
        
        verify(imageProcessingService).processPdf(file);
        verify(aiClient).analyzeImages(pdfImages, question);
    }
    
    @Test
    void testAnalyzeDocument_InvalidFileType_ShouldThrowException() {
        MultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", new byte[]{1, 2, 3}
        );
        String question = "分析这个文件";
        
        when(imageProcessingService.isImageFile(anyString())).thenReturn(false);
        when(imageProcessingService.isPdfFile(anyString())).thenReturn(false);
        
        assertThrows(InvalidFileTypeException.class, () -> {
            analysisService.analyzeDocument(file, question);
        });
    }
    
    @Test
    void testAnalyzeDocument_NullFile_ShouldThrowException() {
        assertThrows(BusinessException.class, () -> {
            analysisService.analyzeDocument(null, "问题");
        });
    }
    
    @Test
    void testAnalyzeDocument_EmptyQuestion_ShouldThrowException() {
        MultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        
        assertThrows(BusinessException.class, () -> {
            analysisService.analyzeDocument(file, "");
        });
    }
}
