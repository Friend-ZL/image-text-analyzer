package com.portfolio.analyzer.service;

import com.portfolio.analyzer.exception.InvalidFileTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageProcessingServiceTest {
    
    private ImageProcessingService service;
    
    @BeforeEach
    void setUp() {
        service = new ImageProcessingService();
        ReflectionTestUtils.setField(service, "maxPdfPages", 10);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 150);
        ReflectionTestUtils.setField(service, "supportedImageTypes", 
            "image/jpeg,image/png,image/gif,image/webp,image/jpg");
    }
    
    @Test
    void testProcessImage_ValidJpeg_ShouldReturnImageBytes() throws IOException {
        // 创建测试图片
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);
        byte[] imageBytes = baos.toByteArray();
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            imageBytes
        );
        
        List<byte[]> result = service.processImage(file);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).length > 0);
    }
    
    @Test
    void testProcessImage_ValidPng_ShouldReturnImageBytes() throws IOException {
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.png",
            "image/png",
            imageBytes
        );
        
        List<byte[]> result = service.processImage(file);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).length > 0);
    }
    
    @Test
    void testProcessImage_InvalidType_ShouldThrowException() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "test content".getBytes()
        );
        
        assertThrows(InvalidFileTypeException.class, () -> {
            service.processImage(file);
        });
    }
    
    @Test
    void testIsImageFile_ValidTypes_ShouldReturnTrue() {
        assertTrue(service.isImageFile("image/jpeg"));
        assertTrue(service.isImageFile("image/png"));
        assertTrue(service.isImageFile("image/jpg"));
        assertTrue(service.isImageFile("image/gif"));
        assertTrue(service.isImageFile("image/webp"));
    }
    
    @Test
    void testIsImageFile_InvalidTypes_ShouldReturnFalse() {
        assertFalse(service.isImageFile("text/plain"));
        assertFalse(service.isImageFile("application/pdf"));
        assertFalse(service.isImageFile("video/mp4"));
    }
    
    @Test
    void testIsPdfFile_ValidPdf_ShouldReturnTrue() {
        assertTrue(service.isPdfFile("application/pdf"));
    }
    
    @Test
    void testIsPdfFile_InvalidTypes_ShouldReturnFalse() {
        assertFalse(service.isPdfFile("image/jpeg"));
        assertFalse(service.isPdfFile("text/plain"));
    }
}
