package com.portfolio.analyzer.util;

import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 日志脱敏工具类
 * <p>
 * 功能：
 * - question: 不记录原文，只记录长度
 * - 文件名: 不记录原文，只记录扩展名、大小和内容哈希前 8 位
 */
public class LogSanitizer {
    
    /**
     * 脱敏 question，只返回长度信息
     * 
     * @param question 用户问题
     * @return 脱敏后的信息，例如 "[length=25]"
     */
    public static String sanitizeQuestion(String question) {
        if (question == null) {
            return "[null]";
        }
        return "[length=" + question.length() + "]";
    }
    
    /**
     * 脱敏文件信息，返回扩展名、大小和内容哈希前 8 位
     * 
     * @param file 上传文件
     * @return 脱敏后的信息，例如 "[ext=.jpg, size=102400, hash=a1b2c3d4]"
     */
    public static String sanitizeFile(MultipartFile file) {
        if (file == null) {
            return "[null]";
        }
        
        String filename = file.getOriginalFilename();
        String extension = getFileExtension(filename);
        long size = file.getSize();
        String hashPrefix = "[error]";
        
        try {
            byte[] content = file.getBytes();
            hashPrefix = computeHashPrefix(content);
        } catch (Exception e) {
            // 如果获取内容失败，使用文件名计算哈希
            if (filename != null) {
                hashPrefix = computeHashPrefix(filename.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        return String.format("[ext=%s, size=%d, hash=%s]", extension, size, hashPrefix);
    }
    
    /**
     * 脱敏文件名，返回扩展名
     * 
     * @param filename 文件名
     * @param contentType 内容类型
     * @return 脱敏后的信息，例如 "[ext=.jpg, type=image/jpeg]"
     */
    public static String sanitizeFilename(String filename, String contentType) {
        String extension = getFileExtension(filename);
        return String.format("[ext=%s, type=%s]", extension, contentType);
    }
    
    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "[none]";
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "[none]";
    }
    
    /**
     * 计算内容的 SHA-256 哈希前 8 位
     */
    private static String computeHashPrefix(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            
            // 取前 4 字节，转为 8 位 hex
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "[error]";
        }
    }
}
