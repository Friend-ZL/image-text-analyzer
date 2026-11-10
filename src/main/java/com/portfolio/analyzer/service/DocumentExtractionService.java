package com.portfolio.analyzer.service;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.DocumentPage;
import com.portfolio.analyzer.dto.SourceType;
import com.portfolio.analyzer.exception.FileSizeLimitException;
import com.portfolio.analyzer.exception.InvalidFileContentException;
import com.portfolio.analyzer.exception.InvalidFileTypeException;
import com.portfolio.analyzer.util.FileTypeDetector;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档提取服务
 * 
 * <p>按页提取文档内容：
 * <ul>
 *   <li>纯文本（txt/md）：读取文本，按长度分页</li>
 *   <li>PDF：逐页提取文本层，文本不足时走VLM OCR</li>
 *   <li>图片：作为单页，通过VLM提取文字和描述</li>
 * </ul>
 * 
 * @since Stage 2 Refactor
 */
@Service
public class DocumentExtractionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentExtractionService.class);
    
    private static final List<String> SUPPORTED_TYPES = List.of(
        "text/plain",
        "text/markdown",
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    );
    
    private static final int CHARS_PER_TEXT_PAGE = 1000;
    
    private final RagConfig ragConfig;
    private final MultimodalAIClient aiClient;
    private final ImageProcessingService imageProcessingService;
    
    public DocumentExtractionService(
            RagConfig ragConfig,
            MultimodalAIClient aiClient,
            ImageProcessingService imageProcessingService) {
        this.ragConfig = ragConfig;
        this.aiClient = aiClient;
        this.imageProcessingService = imageProcessingService;
    }
    
    /**
     * 提取文档内容（按页）
     * 
     * @param file 上传的文件
     * @return 文档页面列表
     * @throws IOException IO异常
     */
    public List<DocumentPage> extractPages(MultipartFile file) throws IOException {
        validateFile(file);
        
        byte[] fileBytes = file.getBytes();
        String mimeType = FileTypeDetector.detectMimeType(fileBytes, file.getOriginalFilename());
        
        logger.info("Extracting pages from file: name={}, size={}, mimeType={}", 
                file.getOriginalFilename(), file.getSize(), mimeType);
        
        if (!SUPPORTED_TYPES.contains(mimeType)) {
            throw new InvalidFileTypeException(
                "不支持的文件类型: " + mimeType + "。支持的类型: " + String.join(", ", SUPPORTED_TYPES)
            );
        }
        
        if (FileTypeDetector.isText(mimeType)) {
            return extractTextPages(fileBytes);
        } else if (FileTypeDetector.isPdf(mimeType)) {
            return extractPdfPages(fileBytes);
        } else if (FileTypeDetector.isImage(mimeType)) {
            return extractImagePage(fileBytes);
        } else {
            throw new InvalidFileTypeException("不支持的文件类型: " + mimeType);
        }
    }
    
    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        if (file.getSize() > ragConfig.getMaxIngestSizeBytes()) {
            throw new FileSizeLimitException(
                String.format("文件大小超过限制 (%d MB)", ragConfig.getMaxIngestSizeMb())
            );
        }
    }
    
    /**
     * 提取纯文本文件的页面
     */
    private List<DocumentPage> extractTextPages(byte[] fileBytes) {
        String content = decodeText(fileBytes);
        
        if (content.trim().isEmpty()) {
            throw new InvalidFileContentException("文件内容为空");
        }
        
        List<DocumentPage> pages = new ArrayList<>();
        int pageNumber = 1;
        
        for (int start = 0; start < content.length(); start += CHARS_PER_TEXT_PAGE) {
            int end = Math.min(start + CHARS_PER_TEXT_PAGE, content.length());
            String pageContent = content.substring(start, end);
            pages.add(new DocumentPage(pageNumber++, pageContent, SourceType.TEXT));
        }
        
        logger.info("Extracted {} text pages", pages.size());
        return pages;
    }
    
    /**
     * 解码文本（严格模式）
     */
    private String decodeText(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (Exception e) {
            throw new InvalidFileContentException("文件不是有效的UTF-8文本", e);
        }
    }
    
    /**
     * 提取PDF页面
     */
    private List<DocumentPage> extractPdfPages(byte[] pdfBytes) throws IOException {
        List<DocumentPage> pages = new ArrayList<>();
        
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            
            if (totalPages > ragConfig.getMaxPdfPages()) {
                throw new FileSizeLimitException(
                    String.format("PDF页数超过限制 (%d 页)", ragConfig.getMaxPdfPages())
                );
            }
            
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            
            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String text = stripper.getText(document).trim();
                
                if (text.length() >= ragConfig.getMinTextLengthForOcr()) {
                    pages.add(new DocumentPage(pageNum, text, SourceType.TEXT));
                    logger.debug("PDF page {} extracted as text ({} chars)", pageNum, text.length());
                } else {
                    logger.info("PDF page {} has insufficient text, using VLM OCR", pageNum);
                    BufferedImage image = renderer.renderImageWithDPI(pageNum - 1, 150);
                    String ocrContent = performVlmOcr(image);
                    pages.add(new DocumentPage(pageNum, ocrContent, SourceType.VLM_OCR));
                }
            }
        }
        
        logger.info("Extracted {} PDF pages", pages.size());
        return pages;
    }
    
    /**
     * 使用VLM进行OCR
     */
    private String performVlmOcr(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        
        String prompt = "请识别图片中的所有文字，并描述图片中的表格、图表等内容。" +
                "如果有表格，请用Markdown表格格式输出。" +
                "保持原文的格式和结构。";
        
        return aiClient.analyzeImages(List.of(imageBytes), prompt);
    }
    
    /**
     * 提取图片页面
     */
    private List<DocumentPage> extractImagePage(byte[] imageBytes) {
        logger.info("Extracting image as single page with VLM");
        
        String prompt = "请详细描述这张图片的内容，包括：\n" +
                "1. 图片中的所有文字（如果有）\n" +
                "2. 图片中的主要对象和场景\n" +
                "3. 图表、表格等结构化内容（用Markdown格式）\n" +
                "请按重要性排序输出。";
        
        String content = aiClient.analyzeImages(List.of(imageBytes), prompt);
        
        List<DocumentPage> pages = new ArrayList<>();
        pages.add(new DocumentPage(1, content, SourceType.IMAGE_DESC));
        
        return pages;
    }
}
