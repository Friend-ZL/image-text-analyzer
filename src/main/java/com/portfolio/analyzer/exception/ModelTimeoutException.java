package com.portfolio.analyzer.exception;

public class ModelTimeoutException extends RuntimeException {
    
    public ModelTimeoutException(String message) {
        super(message);
    }
    
    public ModelTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
