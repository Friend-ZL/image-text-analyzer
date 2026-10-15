package com.portfolio.analyzer.exception;

public class FileSizeLimitException extends RuntimeException {
    
    public FileSizeLimitException(String message) {
        super(message);
    }
    
    public FileSizeLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
