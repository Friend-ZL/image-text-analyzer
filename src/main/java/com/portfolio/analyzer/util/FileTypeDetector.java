package com.portfolio.analyzer.util;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件类型检测工具（基于魔数）
 * 
 * <p>使用Apache Tika检测文件真实类型，不依赖扩展名或Content-Type。
 * 
 * @since Stage 2 Refactor
 */
public class FileTypeDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(FileTypeDetector.class);
    private static final Tika tika = new Tika();
    
    /**
     * 检测文件类型（基于魔数）
     * 
     * @param bytes 文件字节数组
     * @param filename 文件名（用于日志）
     * @return MIME类型，例如 "application/pdf", "image/jpeg"
     */
    public static String detectMimeType(byte[] bytes, String filename) {
        try {
            String mimeType = tika.detect(bytes);
            logger.debug("Detected MIME type for {}: {}", filename, mimeType);
            return mimeType;
        } catch (Exception e) {
            logger.warn("Failed to detect MIME type for {}, falling back to application/octet-stream", 
                    filename, e);
            return "application/octet-stream";
        }
    }
    
    /**
     * 检测文件类型（基于流）
     * 
     * @param inputStream 输入流
     * @param filename 文件名（用于日志）
     * @return MIME类型
     * @throws IOException IO异常
     */
    public static String detectMimeType(InputStream inputStream, String filename) throws IOException {
        String mimeType = tika.detect(inputStream);
        logger.debug("Detected MIME type for {}: {}", filename, mimeType);
        return mimeType;
    }
    
    /**
     * 检查是否为图片类型
     */
    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }
    
    /**
     * 检查是否为PDF
     */
    public static boolean isPdf(String mimeType) {
        return "application/pdf".equals(mimeType);
    }
    
    /**
     * 检查是否为文本类型
     */
    public static boolean isText(String mimeType) {
        return mimeType != null && (mimeType.startsWith("text/") || 
                "application/json".equals(mimeType) ||
                "application/xml".equals(mimeType));
    }
}
