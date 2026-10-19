package com.portfolio.analyzer.exception;

/**
 * 文件内容无效异常
 * 
 * <p>用于文件编码错误、内容格式错误等场景。
 * 
 * @since Stage 2 Refactor
 */
public class InvalidFileContentException extends RuntimeException {
    
    public InvalidFileContentException(String message) {
        super(message);
    }
    
    public InvalidFileContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
