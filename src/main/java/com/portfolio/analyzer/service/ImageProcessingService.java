package com.portfolio.analyzer.service;

import com.portfolio.analyzer.exception.InvalidFileTypeException;
import com.portfolio.analyzer.util.LogSanitizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ImageProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingService.class);
    
    @Value("${app.analysis.max-pdf-pages:10}")
    private int maxPdfPages;
    
    @Value("${app.analysis.pdf-render-dpi:150}")
    private int pdfRenderDpi;
    
    @Value("${app.analysis.supported-image-types}")
    private String supportedImageTypes;
    
    /**
     * 处理图片文件，返回图片字节数组列表
     */
    public List<byte[]> processImage(MultipartFile file) {
        logger.info("Processing image file: {}", LogSanitizer.sanitizeFilename(file.getOriginalFilename(), file.getContentType()));
        
        if (!isImageFile(file.getContentType())) {
            throw new InvalidFileTypeException("不支持的图片类型: " + file.getContentType());
        }
        
        try {
            byte[] imageBytes = file.getBytes();
            return List.of(imageBytes);
        } catch (IOException e) {
            logger.error("Failed to read image file: {}", LogSanitizer.sanitizeFilename(file.getOriginalFilename(), file.getContentType()), e);
            throw new RuntimeException("读取图片文件失败", e);
        }
    }
    
    /**
     * 处理PDF文件，将每页渲染为图片
     */
    public List<byte[]> processPdf(MultipartFile file) {
        logger.info("Processing PDF file: {}", LogSanitizer.sanitizeFilename(file.getOriginalFilename(), file.getContentType()));
        
        if (!isPdfFile(file.getContentType())) {
            throw new InvalidFileTypeException("不是PDF文件: " + file.getContentType());
        }
        
        List<byte[]> images = new ArrayList<>();
        
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            int pageCount = Math.min(document.getNumberOfPages(), maxPdfPages);
            logger.info("PDF has {} pages, processing first {}", document.getNumberOfPages(), pageCount);
            
            PDFRenderer renderer = new PDFRenderer(document);
            
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, pdfRenderDpi);
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                images.add(baos.toByteArray());
                
                logger.debug("Rendered page {} of {}", i + 1, pageCount);
            }
            
            logger.info("Successfully processed {} pages from PDF", images.size());
            return images;
            
        } catch (IOException e) {
            logger.error("Failed to process PDF file: {}", LogSanitizer.sanitizeFilename(file.getOriginalFilename(), file.getContentType()), e);
            throw new RuntimeException("处理PDF文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否为支持的图片类型
     */
    public boolean isImageFile(String contentType) {
        if (contentType == null) {
            return false;
        }
        
        List<String> supportedTypes = Arrays.asList(supportedImageTypes.split(","));
        return supportedTypes.stream()
            .anyMatch(type -> type.trim().equalsIgnoreCase(contentType));
    }
    
    /**
     * 检查是否为PDF文件
     */
    public boolean isPdfFile(String contentType) {
        return "application/pdf".equalsIgnoreCase(contentType);
    }
}
